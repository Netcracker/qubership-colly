package org.qubership.colly;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.qubership.colly.cloudpassport.ClusterInfo;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class CollyStorageTest {

    @Inject
    CollyStorage collyStorage;

    @InjectMock
    @RestClient
    EnvgeneInventoryServiceRest envgeneInventoryService;

    @InjectMock
    ClusterResourcesLoader clusterResourcesLoader;

    @InjectMock
    ClusterSyncLock clusterSyncLock;

    @BeforeEach
    void setUp() {
        when(clusterSyncLock.tryAcquire(any(), any())).thenReturn(true);
    }

    @Test
    void syncAllClusters_shouldLoadClusterResourcesInParallel() throws InterruptedException {
        ClusterInfo cluster1 = new ClusterInfo("1", "cluster1", "token1", "host1", "host1", Set.of(), null, null);
        ClusterInfo cluster2 = new ClusterInfo("2", "cluster2", "token2", "host2", "host2", Set.of(), null, null);
        ClusterInfo cluster3 = new ClusterInfo("3", "cluster3", "token3", "host3", "host3", Set.of(), null, null);
        List<ClusterInfo> clusterInfos = List.of(cluster1, cluster2, cluster3);

        when(envgeneInventoryService.getClusterInfos()).thenReturn(clusterInfos);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(3);
        AtomicInteger concurrentExecutions = new AtomicInteger(0);
        AtomicInteger maxConcurrentExecutions = new AtomicInteger(0);

        doAnswer(invocation -> {
            startLatch.await(5, TimeUnit.SECONDS);
            int current = concurrentExecutions.incrementAndGet();
            maxConcurrentExecutions.updateAndGet(max -> Math.max(max, current));
            Thread.sleep(100);
            concurrentExecutions.decrementAndGet();
            completeLatch.countDown();
            return null;
        }).when(clusterResourcesLoader).loadClusterResources(any(ClusterInfo.class));

        Thread executionThread = new Thread(() -> collyStorage.syncAllClusters());
        executionThread.start();

        Thread.sleep(50);
        startLatch.countDown();

        assertTrue(completeLatch.await(10, TimeUnit.SECONDS), "All cluster resource loading should complete within timeout");
        executionThread.join(5000);

        verify(clusterResourcesLoader, times(3)).loadClusterResources(any(ClusterInfo.class));

        ArgumentCaptor<ClusterInfo> captor = ArgumentCaptor.forClass(ClusterInfo.class);
        verify(clusterResourcesLoader, times(3)).loadClusterResources(captor.capture());

        List<String> processedClusters = captor.getAllValues().stream().map(ClusterInfo::name).toList();
        assertTrue(processedClusters.contains("cluster1"));
        assertTrue(processedClusters.contains("cluster2"));
        assertTrue(processedClusters.contains("cluster3"));

        assertTrue(maxConcurrentExecutions.get() >= 2,
                "Expected at least 2 concurrent executions, but got: " + maxConcurrentExecutions.get());
    }

    @Test
    void syncAllClusters_shouldHandleExceptionInParallelExecution() {
        ClusterInfo cluster1 = new ClusterInfo("1", "cluster1", "token1", "host1", "host1", Set.of(), null, null);
        ClusterInfo cluster2 = new ClusterInfo("2", "cluster2", "token2", "host2", "host2", Set.of(), null, null);
        List<ClusterInfo> clusterInfos = List.of(cluster1, cluster2);

        when(envgeneInventoryService.getClusterInfos()).thenReturn(clusterInfos);

        doAnswer(invocation -> {
            ClusterInfo passport = invocation.getArgument(0);
            if ("cluster1".equals(passport.name())) {
                throw new RuntimeException("Simulated cluster1 failure");
            }
            return null;
        }).when(clusterResourcesLoader).loadClusterResources(any(ClusterInfo.class));

        assertDoesNotThrow(() -> collyStorage.syncAllClusters());

        verify(clusterResourcesLoader, times(2)).loadClusterResources(any(ClusterInfo.class));
    }

    @Test
    void syncAllClusters_shouldHandleEmptyClusterList() {
        when(envgeneInventoryService.getClusterInfos()).thenReturn(List.of());

        assertDoesNotThrow(() -> collyStorage.syncAllClusters());

        verify(clusterResourcesLoader, never()).loadClusterResources(any(ClusterInfo.class));
    }

    @Test
    void syncAllClusters_load_cloud_passports_once_and_load_cluster_resources_for_each_cluster() {
        ClusterInfo cluster = new ClusterInfo("1", "test-cluster", "token", "host", "host", Set.of(), null, null);
        when(envgeneInventoryService.getClusterInfos()).thenReturn(List.of(cluster));

        collyStorage.syncAllClusters();

        verify(envgeneInventoryService, times(1)).getClusterInfos();
        verify(clusterResourcesLoader, times(1)).loadClusterResources(cluster);
    }

    @Test
    void syncAllClusters_skipsClusterIfLockNotAcquired() {
        ClusterInfo cluster = new ClusterInfo("1", "cluster1", "token", "host", "host", Set.of(), null, null);
        when(envgeneInventoryService.getClusterInfos()).thenReturn(List.of(cluster));
        when(clusterSyncLock.tryAcquire(eq("1"), any(Duration.class))).thenReturn(false);

        collyStorage.syncAllClusters();

        verify(clusterResourcesLoader, never()).loadClusterResources(any());
        verify(clusterSyncLock, never()).release(any());
    }

    @Test
    void syncAllClusters_releasesLockAfterCompletion() {
        ClusterInfo cluster = new ClusterInfo("1", "cluster1", "token", "host", "host", Set.of(), null, null);
        when(envgeneInventoryService.getClusterInfos()).thenReturn(List.of(cluster));

        collyStorage.syncAllClusters();
        collyStorage.syncAllClusters();

        verify(clusterSyncLock, times(2)).tryAcquire(eq("1"), any(Duration.class));
        verify(clusterSyncLock, times(2)).release("1");
        verify(clusterResourcesLoader, times(2)).loadClusterResources(cluster);
    }

    @Test
    void syncAllClusters_releasesLockEvenIfClusterThrows() {
        ClusterInfo cluster = new ClusterInfo("1", "cluster1", "token", "host", "host", Set.of(), null, null);
        when(envgeneInventoryService.getClusterInfos()).thenReturn(List.of(cluster));
        doThrow(new RuntimeException("Simulated failure"))
                .when(clusterResourcesLoader).loadClusterResources(any());

        assertDoesNotThrow(() -> collyStorage.syncAllClusters());

        verify(clusterSyncLock, times(1)).release("1");
    }

    @Test
    void onStart_invokesSyncAllClusters() {
        when(envgeneInventoryService.getClusterInfos()).thenReturn(List.of());

        collyStorage.onStart(null);

        verify(envgeneInventoryService, times(1)).getClusterInfos();
    }

    @Test
    void executeTask_shouldExecuteInCorrectOrder() {
        ClusterInfo cluster1 = new ClusterInfo("1", "cluster1", "token1", "host1", "host1", Set.of(), null, null);
        ClusterInfo cluster2 = new ClusterInfo("2", "cluster2", "token2", "host2", "host2", Set.of(), null, null);
        List<ClusterInfo> clusterInfos = List.of(cluster1, cluster2);

        when(envgeneInventoryService.getClusterInfos()).thenReturn(clusterInfos);

        CountDownLatch loadCompleteLatch = new CountDownLatch(2);

        doAnswer(invocation -> {
            Thread.sleep(50);
            loadCompleteLatch.countDown();
            return null;
        }).when(clusterResourcesLoader).loadClusterResources(any(ClusterInfo.class));

        long startTime = System.currentTimeMillis();
        collyStorage.syncAllClusters();
        long endTime = System.currentTimeMillis();

        verify(envgeneInventoryService, times(1)).getClusterInfos();
        verify(clusterResourcesLoader, times(2)).loadClusterResources(any(ClusterInfo.class));
        assertEquals(0, loadCompleteLatch.getCount());

        assertTrue(endTime - startTime < 150,
                "Parallel execution should be faster than sequential. Took: " + (endTime - startTime) + "ms");
    }
}
