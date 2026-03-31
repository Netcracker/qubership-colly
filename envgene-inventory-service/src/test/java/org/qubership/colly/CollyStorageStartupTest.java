package org.qubership.colly;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.qubership.colly.db.ClusterRepository;
import org.qubership.colly.db.EnvironmentRepository;
import org.qubership.colly.db.ProjectRepository;
import org.qubership.colly.projectrepo.ProjectRepoLoader;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

;

@QuarkusComponentTest
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
