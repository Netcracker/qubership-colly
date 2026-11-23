package org.qubership.colly;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.colly.cloudpassport.*;
import org.qubership.colly.db.ClusterRepository;
import org.qubership.colly.db.EnvironmentRepository;
import org.qubership.colly.db.ProjectRepository;
import org.qubership.colly.db.data.*;
import org.qubership.colly.projectrepo.InstanceRepository;
import org.qubership.colly.projectrepo.ProjectRepoLoader;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@QuarkusTest
class CollyStorageStartupTest {

    @Inject
    CollyStorage collyStorage;

    @InjectMock
    ProjectRepoLoader projectRepoLoader;

    @InjectMock
    CloudPassportLoader cloudPassportLoader;

    @InjectMock
    ClusterRepository clusterRepository;

    @InjectMock
    EnvironmentRepository environmentRepository;

    @InjectMock
    ProjectRepository projectRepository;

    @InjectMock
    UpdateEnvironmentService updateEnvironmentService;

    @InjectMock
    ParamsetService paramsetService;

    @BeforeEach
    void setUp() {
        when(cloudPassportLoader.loadCloudPassports(any())).thenReturn(new CloudPassportLoadResult(List.of(), Set.of()));
    }

    @Test
    void startup_shouldNotFailWhenInstanceRepositoryFailed() {
        when(cloudPassportLoader.loadCloudPassports(any()))
                .thenReturn(new CloudPassportLoadResult(List.of(), Set.of("broken")));

        assertDoesNotThrow(() -> collyStorage.onStart(new StartupEvent()));
    }

    @Test
    void startup_shouldNotFailWhenRedisHasNamespaceWithoutName() {
        Environment stored = new Environment("env-id", "env");
        stored.addNamespace(new Namespace());
        Cluster cluster = Cluster.builder().id("cluster-id").build();
        cluster.setName("cluster");
        when(clusterRepository.findByProjectIdAndName("project", "cluster")).thenReturn(cluster);
        when(environmentRepository.findByNameAndClusterId("env", "cluster-id")).thenReturn(stored);
        CloudPassportEnvironment environment = new CloudPassportEnvironment("env", null,
                List.of(new CloudPassportNamespace("env-core", "core")), List.of(), List.of(), List.of(),
                EnvironmentStatus.FREE, null, EnvironmentType.ENVIRONMENT, null, List.of(), List.of(), List.of(),
                false, CmApproach.NO_CMDB, List.of(), null, null, null);
        CloudPassport passport = new CloudPassport("cluster", null, null, null, Set.of(environment), null,
                new GitInfo(new InstanceRepository("url", "main", "token", "cn"), "folder", "project", null),
                null, null, null, null, null, null);
        when(cloudPassportLoader.loadCloudPassports(any()))
                .thenReturn(new CloudPassportLoadResult(List.of(passport), Set.of()));

        assertDoesNotThrow(() -> collyStorage.onStart(new StartupEvent()));
        assertEquals(List.of("env-core"), stored.getNamespaces().stream().map(Namespace::getName).toList());
    }

    @Test
    void syncAll_shouldBeCalledOnStartup() {
        collyStorage.onStart(new StartupEvent());

        verify(projectRepoLoader).loadProjects();
    }

    @Test
    void syncAll_shouldSkipIfAlreadyRunning() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch firstCanFinish = new CountDownLatch(1);
        AtomicInteger callCount = new AtomicInteger(0);

        doAnswer(inv -> {
            callCount.incrementAndGet();
            firstStarted.countDown();
            firstCanFinish.await();
            return List.of();
        }).when(projectRepoLoader).loadProjects();

        var executor = Executors.newFixedThreadPool(2);
        Future<?> first = executor.submit(() -> collyStorage.syncAll());
        firstStarted.await();

        Future<?> second = executor.submit(() -> collyStorage.syncAll());
        second.get();

        firstCanFinish.countDown();
        first.get();

        assertEquals(1, callCount.get(), "syncAll should execute only once when called concurrently");
        executor.shutdown();
    }
}
