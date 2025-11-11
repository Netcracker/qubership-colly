package org.qubership.colly;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.qubership.colly.cloudpassport.ClusterInfo;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class CollyStorageExceptionHandlingTest {

    @Inject
    CollyStorage collyStorage;

    @InjectMock
    @RestClient
    EnvgeneInventoryServiceRest envgeneInventoryService;

    @InjectMock
    ClusterResourcesLoader clusterResourcesLoader;

    @Test
    void executeTask_shouldContinueExecutionWhenSomeClustersFail() throws InterruptedException {
        ClusterInfo cluster1 = new ClusterInfo("1", "stable-cluster", "token1", "host1", Set.of(), null);
        ClusterInfo cluster2 = new ClusterInfo("2", "failing-cluster", "token2", "host2", Set.of(), null);
        ClusterInfo cluster3 = new ClusterInfo("3", "another-stable-cluster", "token3", "host3", Set.of(), null);
        List<ClusterInfo> clusterInfos = List.of(cluster1, cluster2, cluster3);

        when(envgeneInventoryService.getClusterInfos()).thenReturn(clusterInfos);

        CountDownLatch executionLatch = new CountDownLatch(3);
        AtomicInteger successfulExecutions = new AtomicInteger(0);

        doAnswer(invocation -> {
            ClusterInfo passport = invocation.getArgument(0);
            try {
                if ("failing-cluster".equals(passport.name())) {
                    throw new RuntimeException("Simulated cluster failure");
                }
                successfulExecutions.incrementAndGet();
                return null;
            } finally {
                executionLatch.countDown();
            }
        }).when(clusterResourcesLoader).loadClusterResources(any(ClusterInfo.class));

        assertDoesNotThrow(() -> collyStorage.executeTask());

        // Wait for all executions to complete
        assertTrue(executionLatch.await(5, TimeUnit.SECONDS));

        verify(clusterResourcesLoader, times(3)).loadClusterResources(any(ClusterInfo.class));
        assertEquals(2, successfulExecutions.get(), "Two clusters should have succeeded");

        // Verify all clusters were attempted
        ArgumentCaptor<ClusterInfo> captor = ArgumentCaptor.forClass(ClusterInfo.class);
        verify(clusterResourcesLoader, times(3)).loadClusterResources(captor.capture());

        List<String> processedClusters = captor.getAllValues().stream()
                .map(ClusterInfo::name)
                .toList();

        assertThat(processedClusters, containsInAnyOrder("stable-cluster", "another-stable-cluster", "failing-cluster"));
    }

    @Test
    void executeTask_shouldHandleAllClustersFailingGracefully() {
        ClusterInfo cluster1 = new ClusterInfo("1", "failing-cluster1", "token1", "host1", Set.of(), null);
        ClusterInfo cluster2 = new ClusterInfo("2", "failing-cluster2", "token2", "host2", Set.of(), null);
        List<ClusterInfo> clusterInfos = List.of(cluster1, cluster2);

        when(envgeneInventoryService.getClusterInfos()).thenReturn(clusterInfos);

        doThrow(new RuntimeException("Simulated failure"))
                .when(clusterResourcesLoader).loadClusterResources(any(ClusterInfo.class));

        assertDoesNotThrow(() -> collyStorage.executeTask());
        verify(clusterResourcesLoader, times(2)).loadClusterResources(any(ClusterInfo.class));
    }

    @Test
    void executeTask_shouldHandleInterruptedException() throws InterruptedException {
        ClusterInfo cluster = new ClusterInfo("1", "interrupted-cluster", "token", "host", Set.of(), null);
        when(envgeneInventoryService.getClusterInfos()).thenReturn(List.of(cluster));

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch interruptLatch = new CountDownLatch(1);

        doAnswer(invocation -> {
            startLatch.countDown();
            // Wait to be interrupted
            try {
                interruptLatch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during execution", e);
            }
            return null;
        }).when(clusterResourcesLoader).loadClusterResources(any(ClusterInfo.class));

        Thread executionThread = new Thread(() -> collyStorage.executeTask());
        executionThread.start();

        // Wait for execution to start
        assertTrue(startLatch.await(5, TimeUnit.SECONDS));

        // Interrupt the execution
        executionThread.interrupt();
        interruptLatch.countDown();

        // Wait for completion
        executionThread.join(5000);

        verify(clusterResourcesLoader, times(1)).loadClusterResources(cluster);
        assertFalse(executionThread.isAlive(), "Execution thread should have completed");
    }

    @Test
    void executeTask_shouldHandleCloudPassportLoaderException() {
        when(envgeneInventoryService.getClusterInfos())
                .thenThrow(new RuntimeException("Failed to load cloud passports"));

        assertThrows(RuntimeException.class, () -> collyStorage.executeTask());

        verify(clusterResourcesLoader, never()).loadClusterResources(any(ClusterInfo.class));
    }

    @Test
    void executeTask_shouldHandleMixedExceptionTypes() {
        ClusterInfo cluster1 = new ClusterInfo("1", "runtime-exception-cluster", "token1", "host1", Set.of(), null);
        ClusterInfo cluster2 = new ClusterInfo("2", "illegal-argument-cluster", "token2", "host2", Set.of(), null);
        ClusterInfo cluster3 = new ClusterInfo("3", "successful-cluster", "token3", "host3", Set.of(), null);
        List<ClusterInfo> clusterInfos = List.of(cluster1, cluster2, cluster3);

        when(envgeneInventoryService.getClusterInfos()).thenReturn(clusterInfos);

        AtomicInteger successCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            ClusterInfo passport = invocation.getArgument(0);
            return switch (passport.name()) {
                case "runtime-exception-cluster" -> throw new RuntimeException("Runtime exception");
                case "illegal-argument-cluster" -> throw new IllegalArgumentException("Illegal argument");
                case "successful-cluster" -> {
                    successCount.incrementAndGet();
                    yield null;
                }
                default -> null;
            };
        }).when(clusterResourcesLoader).loadClusterResources(any(ClusterInfo.class));

        assertDoesNotThrow(() -> collyStorage.executeTask());

        assertEquals(1, successCount.get(), "One cluster should have succeeded");
        verify(clusterResourcesLoader, times(3)).loadClusterResources(any(ClusterInfo.class));
    }
}
