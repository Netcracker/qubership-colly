package org.qubership.colly;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.qubership.colly.cloudpassport.ClusterInfo;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@QuarkusTest
class CollyStoragePerformanceTest {

    @Inject
    CollyStorage collyStorage;

    @InjectMock
    @RestClient
    EnvgeneInventoryServiceRest envgeneInventoryService;

    @InjectMock
    ClusterResourcesLoader clusterResourcesLoader;

    @Test
    void syncAllClusters_performanceTest_shouldBeSignificantlyFasterThanSequential() {
        final int clusterCount = 10;
        final int simulatedWorkTimeMs = 200;

        List<ClusterInfo> clusterInfos = IntStream.range(0, clusterCount)
                .mapToObj(i -> new ClusterInfo("cluster" + i, "cluster" + i, "token" + i, "host" + i, Set.of(), null))
                .toList();

        when(envgeneInventoryService.getClusterInfos()).thenReturn(clusterInfos);

        AtomicInteger executionCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            executionCount.incrementAndGet();
            // Simulate network/IO work
            Thread.sleep(simulatedWorkTimeMs);
            return null;
        }).when(clusterResourcesLoader).loadClusterResources(any(ClusterInfo.class));

        long startTime = System.currentTimeMillis();
        collyStorage.syncAllClusters();
        long endTime = System.currentTimeMillis();

        long actualDuration = endTime - startTime;
        long sequentialDuration = clusterCount * simulatedWorkTimeMs;

        assertEquals(clusterCount, executionCount.get());

        // Parallel execution should be significantly faster than sequential
        // Allow some overhead but expect at least 50% improvement
        long maxExpectedDuration = sequentialDuration / 2;
        assertTrue(actualDuration < maxExpectedDuration,
                String.format("Parallel execution took %dms but should be less than %dms (sequential would take %dms)",
                        actualDuration, maxExpectedDuration, sequentialDuration));

        System.out.println("Performance test results:");
        System.out.printf("  Clusters: %d%n", clusterCount);
        System.out.printf("  Simulated work per cluster: %dms%n", simulatedWorkTimeMs);
        System.out.printf("  Sequential execution time: %dms%n", sequentialDuration);
        System.out.printf("  Parallel execution time: %dms%n", actualDuration);
        System.out.printf("  Performance improvement: %.1fx%n", (double) sequentialDuration / actualDuration);
    }

    @Test
    void syncAllClusters_stressTest_shouldHandleManyClustersConcurrently() {
        final int clusterCount = 50;
        final int simulatedWorkTimeMs = 100;

        List<ClusterInfo> clusterInfos = IntStream.range(0, clusterCount)
                .mapToObj(i -> new ClusterInfo("stress-cluster" + i, "stress-cluster" + i, "token" + i, "host" + i, Set.of(), null))
                .toList();

        when(envgeneInventoryService.getClusterInfos()).thenReturn(clusterInfos);

        AtomicInteger maxConcurrentExecutions = new AtomicInteger(0);
        AtomicInteger currentConcurrentExecutions = new AtomicInteger(0);

        doAnswer(invocation -> {
            int current = currentConcurrentExecutions.incrementAndGet();
            maxConcurrentExecutions.updateAndGet(max -> Math.max(max, current));

            Thread.sleep(simulatedWorkTimeMs);

            currentConcurrentExecutions.decrementAndGet();
            return null;
        }).when(clusterResourcesLoader).loadClusterResources(any(ClusterInfo.class));

        // Act
        long startTime = System.currentTimeMillis();
        collyStorage.syncAllClusters();
        long endTime = System.currentTimeMillis();

        verify(clusterResourcesLoader, times(clusterCount)).loadClusterResources(any(ClusterInfo.class));

        // Should handle high concurrency
        assertTrue(maxConcurrentExecutions.get() >= Math.min(clusterCount, 5),
                "Expected high concurrency but got max: " + maxConcurrentExecutions.get());

        // Should complete much faster than sequential
        long actualDuration = endTime - startTime;
        long sequentialDuration = clusterCount * simulatedWorkTimeMs;
        assertTrue(actualDuration < sequentialDuration / 3,
                String.format("Stress test took %dms but should be much less than %dms",
                        actualDuration, sequentialDuration / 3));

        System.out.println("Stress test results:");
        System.out.printf("  Clusters: %d%n", clusterCount);
        System.out.printf("  Max concurrent executions: %d%n", maxConcurrentExecutions.get());
        System.out.printf("  Execution time: %dms%n", actualDuration);
        System.out.printf("  Performance improvement: %.1fx%n", (double) sequentialDuration / actualDuration);
    }
}
