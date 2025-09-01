package org.qubership.colly;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.qubership.colly.cloudpassport.CloudPassport;

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
        CloudPassport cluster1 = new CloudPassport("stable-cluster", "token1", "host1", Set.of(), null);
        CloudPassport cluster2 = new CloudPassport("failing-cluster", "token2", "host2", Set.of(), null);
        CloudPassport cluster3 = new CloudPassport("another-stable-cluster", "token3", "host3", Set.of(), null);
        List<CloudPassport> cloudPassports = List.of(cluster1, cluster2, cluster3);

        when(envgeneInventoryService.getCloudPassports()).thenReturn(cloudPassports);

        CountDownLatch executionLatch = new CountDownLatch(3);
        AtomicInteger successfulExecutions = new AtomicInteger(0);

        doAnswer(invocation -> {
            CloudPassport passport = invocation.getArgument(0);
            try {
                if ("failing-cluster".equals(passport.name())) {
                    throw new RuntimeException("Simulated cluster failure");
                }
                successfulExecutions.incrementAndGet();
                return null;
            } finally {
                executionLatch.countDown();
            }
        }).when(clusterResourcesLoader).loadClusterResources(any(CloudPassport.class));

        assertDoesNotThrow(() -> collyStorage.executeTask());

        // Wait for all executions to complete
        assertTrue(executionLatch.await(5, TimeUnit.SECONDS));

        verify(clusterResourcesLoader, times(3)).loadClusterResources(any(CloudPassport.class));
        assertEquals(2, successfulExecutions.get(), "Two clusters should have succeeded");

        // Verify all clusters were attempted
        ArgumentCaptor<CloudPassport> captor = ArgumentCaptor.forClass(CloudPassport.class);
        verify(clusterResourcesLoader, times(3)).loadClusterResources(captor.capture());

        List<String> processedClusters = captor.getAllValues().stream()
                .map(CloudPassport::name)
                .toList();

        assertThat(processedClusters, containsInAnyOrder("stable-cluster", "another-stable-cluster", "failing-cluster"));
    }

    @Test
    void executeTask_shouldHandleAllClustersFailingGracefully() {
        CloudPassport cluster1 = new CloudPassport("failing-cluster1", "token1", "host1", Set.of(), null);
        CloudPassport cluster2 = new CloudPassport("failing-cluster2", "token2", "host2", Set.of(), null);
        List<CloudPassport> cloudPassports = List.of(cluster1, cluster2);

        when(envgeneInventoryService.getCloudPassports()).thenReturn(cloudPassports);

        doThrow(new RuntimeException("Simulated failure"))
                .when(clusterResourcesLoader).loadClusterResources(any(CloudPassport.class));

        assertDoesNotThrow(() -> collyStorage.executeTask());
        verify(clusterResourcesLoader, times(2)).loadClusterResources(any(CloudPassport.class));
    }

    @Test
    void executeTask_shouldHandleInterruptedException() throws InterruptedException {
        CloudPassport cluster = new CloudPassport("interrupted-cluster", "token", "host", Set.of(), null);
        when(envgeneInventoryService.getCloudPassports()).thenReturn(List.of(cluster));

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
        }).when(clusterResourcesLoader).loadClusterResources(any(CloudPassport.class));

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
        when(envgeneInventoryService.getCloudPassports())
                .thenThrow(new RuntimeException("Failed to load cloud passports"));

        assertThrows(RuntimeException.class, () -> collyStorage.executeTask());

        verify(clusterResourcesLoader, never()).loadClusterResources(any(CloudPassport.class));
    }

    @Test
    void executeTask_shouldHandleMixedExceptionTypes() {
        CloudPassport cluster1 = new CloudPassport("runtime-exception-cluster", "token1", "host1", Set.of(), null);
        CloudPassport cluster2 = new CloudPassport("illegal-argument-cluster", "token2", "host2", Set.of(), null);
        CloudPassport cluster3 = new CloudPassport("successful-cluster", "token3", "host3", Set.of(), null);
        List<CloudPassport> cloudPassports = List.of(cluster1, cluster2, cluster3);

        when(envgeneInventoryService.getCloudPassports()).thenReturn(cloudPassports);

        AtomicInteger successCount = new AtomicInteger(0);

        doAnswer(invocation -> {
            CloudPassport passport = invocation.getArgument(0);
            return switch (passport.name()) {
                case "runtime-exception-cluster" -> throw new RuntimeException("Runtime exception");
                case "illegal-argument-cluster" -> throw new IllegalArgumentException("Illegal argument");
                case "successful-cluster" -> {
                    successCount.incrementAndGet();
                    yield null;
                }
                default -> null;
            };
        }).when(clusterResourcesLoader).loadClusterResources(any(CloudPassport.class));

        assertDoesNotThrow(() -> collyStorage.executeTask());

        assertEquals(1, successCount.get(), "One cluster should have succeeded");
        verify(clusterResourcesLoader, times(3)).loadClusterResources(any(CloudPassport.class));
    }
}
