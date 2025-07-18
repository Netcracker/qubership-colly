package org.qubership.colly;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.qubership.colly.cloudpassport.CloudPassport;

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
    CloudPassportLoader cloudPassportLoader;

    @InjectMock
    ClusterResourcesLoader clusterResourcesLoader;

    @Test
    void executeTask_shouldLoadClusterResourcesInParallel() throws InterruptedException {
        CloudPassport cluster1 = new CloudPassport("cluster1", "token1", "host1", Set.of(), null);
        CloudPassport cluster2 = new CloudPassport("cluster2", "token2", "host2", Set.of(), null);
        CloudPassport cluster3 = new CloudPassport("cluster3", "token3", "host3", Set.of(), null);
        List<CloudPassport> cloudPassports = List.of(cluster1, cluster2, cluster3);

        when(cloudPassportLoader.loadCloudPassports()).thenReturn(cloudPassports);

        // Use CountDownLatch to synchronize and verify parallel execution
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(3);
        AtomicInteger concurrentExecutions = new AtomicInteger(0);
        AtomicInteger maxConcurrentExecutions = new AtomicInteger(0);

        doAnswer(invocation -> {

            // Wait for all threads to start
            startLatch.await(5, TimeUnit.SECONDS);

            // Track concurrent executions
            int current = concurrentExecutions.incrementAndGet();
            maxConcurrentExecutions.updateAndGet(max -> Math.max(max, current));

            // Simulate some work
            Thread.sleep(100);

            concurrentExecutions.decrementAndGet();
            completeLatch.countDown();

            return null;
        }).when(clusterResourcesLoader).loadClusterResources(any(CloudPassport.class));

        Thread executionThread = new Thread(() -> collyStorage.executeTask());
        executionThread.start();

        // Wait a bit to ensure all threads are created and waiting
        Thread.sleep(50);

        // Release all threads to start executing
        startLatch.countDown();

        // Wait for all executions to complete
        assertTrue(completeLatch.await(10, TimeUnit.SECONDS), "All cluster resource loading should complete within timeout");

        // Wait for main thread to complete
        executionThread.join(5000);

        verify(clusterResourcesLoader, times(3)).loadClusterResources(any(CloudPassport.class));

        // Verify all clusters were processed
        ArgumentCaptor<CloudPassport> captor = ArgumentCaptor.forClass(CloudPassport.class);
        verify(clusterResourcesLoader, times(3)).loadClusterResources(captor.capture());

        List<String> processedClusters = captor.getAllValues().stream()
                .map(CloudPassport::name)
                .toList();

        assertTrue(processedClusters.contains("cluster1"));
        assertTrue(processedClusters.contains("cluster2"));
        assertTrue(processedClusters.contains("cluster3"));

        // Verify parallel execution (at least 2 concurrent executions)
        assertTrue(maxConcurrentExecutions.get() >= 2,
                "Expected at least 2 concurrent executions, but got: " + maxConcurrentExecutions.get());
    }

    @Test
    void executeTask_shouldHandleExceptionInParallelExecution() {
        CloudPassport cluster1 = new CloudPassport("cluster1", "token1", "host1", Set.of(), null);
        CloudPassport cluster2 = new CloudPassport("cluster2", "token2", "host2", Set.of(), null);
        List<CloudPassport> cloudPassports = List.of(cluster1, cluster2);

        when(cloudPassportLoader.loadCloudPassports()).thenReturn(cloudPassports);

        // Mock one cluster to throw exception, other to succeed
        doAnswer(invocation -> {
            CloudPassport passport = invocation.getArgument(0);
            if ("cluster1".equals(passport.name())) {
                throw new RuntimeException("Simulated cluster1 failure");
            }
            return null;
        }).when(clusterResourcesLoader).loadClusterResources(any(CloudPassport.class));

        // Act & Assert - should not throw exception despite one cluster failing
        assertDoesNotThrow(() -> collyStorage.executeTask());

        verify(clusterResourcesLoader, times(2)).loadClusterResources(any(CloudPassport.class));
    }

    @Test
    void executeTask_shouldHandleEmptyClusterList() {
        when(cloudPassportLoader.loadCloudPassports()).thenReturn(List.of());

        assertDoesNotThrow(() -> collyStorage.executeTask());

        verify(clusterResourcesLoader, never()).loadClusterResources(any(CloudPassport.class));
    }


    @Test
    void executeTask_load_cloud_passports_once_and_load_cluster_resources_for_each_cluster() {
        CloudPassport cluster = new CloudPassport("test-cluster", "token", "host", Set.of(), null);
        when(cloudPassportLoader.loadCloudPassports()).thenReturn(List.of(cluster));

        collyStorage.executeTask();

        verify(cloudPassportLoader, times(1)).loadCloudPassports();
        verify(clusterResourcesLoader, times(1)).loadClusterResources(cluster);

    }

    @Test
    void executeTask_shouldExecuteInCorrectOrder() {
        CloudPassport cluster1 = new CloudPassport("cluster1", "token1", "host1", Set.of(), null);
        CloudPassport cluster2 = new CloudPassport("cluster2", "token2", "host2", Set.of(), null);
        List<CloudPassport> cloudPassports = List.of(cluster1, cluster2);

        when(cloudPassportLoader.loadCloudPassports()).thenReturn(cloudPassports);

        CountDownLatch loadStartLatch = new CountDownLatch(2);
        CountDownLatch loadCompleteLatch = new CountDownLatch(2);

        doAnswer(invocation -> {
            loadStartLatch.countDown();
            // Simulate work
            Thread.sleep(50);
            loadCompleteLatch.countDown();
            return null;
        }).when(clusterResourcesLoader).loadClusterResources(any(CloudPassport.class));

        // Act
        long startTime = System.currentTimeMillis();
        collyStorage.executeTask();
        long endTime = System.currentTimeMillis();

        verify(cloudPassportLoader, times(1)).loadCloudPassports();
        verify(clusterResourcesLoader, times(2)).loadClusterResources(any(CloudPassport.class));

        // Verify execution completed (both clusters processed)
        assertEquals(0, loadCompleteLatch.getCount());

        // Verify it took less time than sequential execution would take
        // (2 clusters * 50ms each = 100ms minimum for sequential, should be much less for parallel)
        assertTrue(endTime - startTime < 150,
                "Parallel execution should be faster than sequential. Took: " + (endTime - startTime) + "ms");
    }
}
