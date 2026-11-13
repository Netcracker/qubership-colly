package org.qubership.colly;

import com.netcracker.cloud.security.common.util.M2MUtils;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;
import org.qubership.colly.cloudpassport.ClusterInfo;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.repository.ClusterRepository;
import org.qubership.colly.db.repository.EnvironmentRepository;
import org.qubership.colly.dto.EnvironmentDTO;
import org.qubership.colly.mapper.EnvironmentMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class CollyStorage {

    private static final Duration CLUSTER_SYNC_LOCK_TTL = Duration.ofSeconds(120);

    private final ClusterResourcesLoader clusterResourcesLoader;
    private final ClusterRepository clusterRepository;
    private final EnvironmentRepository environmentRepository;
    private final EnvgeneInventoryServiceRest envgeneInventoryServiceRest;
    private final Executor executor;
    private final EnvironmentMapper environmentMapper;
    private final ClusterSyncLock clusterSyncLock;
    private final AtomicBoolean syncRunning = new AtomicBoolean(false);

    @Inject
    public CollyStorage(ClusterResourcesLoader clusterResourcesLoader,
                        ClusterRepository clusterRepository,
                        EnvironmentRepository environmentRepository,
                        EnvironmentMapper environmentMapper,
                        ClusterSyncLock clusterSyncLock,
                        @RestClient EnvgeneInventoryServiceRest envgeneInventoryServiceRest) {
        this.clusterResourcesLoader = clusterResourcesLoader;
        this.clusterRepository = clusterRepository;
        this.environmentRepository = environmentRepository;
        this.envgeneInventoryServiceRest = envgeneInventoryServiceRest;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.environmentMapper = environmentMapper;
        this.clusterSyncLock = clusterSyncLock;
    }

    /**
     * getClusterInfos with the M2M 401 fallback from the transition plan
     * (docs/design/m2m-kubernetes/0.transition-plan.md, section 0.2): when a request
     * sent with a kubernetes token is rejected with 401 in hybrid mode (peer not
     * migrated yet), retry once with the keycloak token and remember the URL so
     * subsequent calls skip the k8s attempt. k8s mode and user-token requests never
     * retry: their 401s propagate to the caller as is.
     */
    List<ClusterInfo> fetchClusterInfos() {
        try {
            return envgeneInventoryServiceRest.getClusterInfos();
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == Response.Status.UNAUTHORIZED.getStatusCode()
                    && M2mTokenRoute.route() == M2mTokenRoute.Route.KUBERNETES
                    && M2MUtils.isKubernetesM2MFallbackEnabled()) {
                Log.debugf("M2M 401 from inventory-service, retrying with keycloak token");
                M2mTokenRoute.forceKeycloakOnce();
                return envgeneInventoryServiceRest.getClusterInfos();
            }
            throw e;
        } finally {
            M2mTokenRoute.endRequest();
        }
    }

    void onStart(@Observes StartupEvent event) {
        try {
            syncAllClusters();
        } catch (Exception e) {
            Log.warn("Initial sync failed on startup: " + e.getMessage());
        }
    }

    @Scheduled(cron = "{colly.environment-operational-service.cron.schedule}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledSyncAllClusters() {
        try {
            syncAllClusters();
        } catch (Exception e) {
            Log.warn("Scheduled sync failed: " + e.getMessage());
        }
    }

    void syncAllClusters() {
        if (!syncRunning.compareAndSet(false, true)) {
            Log.info("syncAllClusters is already running, skipping");
            return;
        }
        try {
            Log.info("Task for sync resources from clusters has started");
            Date startTime = new Date();
            List<ClusterInfo> clusterInfos = fetchClusterInfos();
            List<String> clusterNames = clusterInfos.stream().map(ClusterInfo::name).toList();
            Log.info("Cloud passports loaded for clusters: " + clusterNames);

            List<CompletableFuture<Void>> futures = clusterInfos.stream()
                    .map(clusterInfo -> CompletableFuture.runAsync(
                                    () -> syncClusterWithLock(clusterInfo), executor)
                            .exceptionally(e -> {
                                Log.errorf("Sync failed for cluster %s (id=%s). %s", clusterInfo.name(), clusterInfo.id(), e.getMessage());
                                return null;
                            }))
                    .toList();

            CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            try {
                allFutures.join();
            } catch (Exception e) {
                Log.errorf("Error occurred while loading cluster resources in parallel. %s", e.getMessage());
            }

            Date loadCompleteTime = new Date();
            long loadingDuration = loadCompleteTime.getTime() - startTime.getTime();
            Log.info("Task for sync resources from clusters has completed. Duration = " + loadingDuration + "ms");
        } finally {
            syncRunning.set(false);
        }
    }

    void syncCluster(String clusterId) {
        if (clusterId == null || clusterId.isEmpty()) {
            throw new IllegalArgumentException("Cluster id is null");
        }
        List<ClusterInfo> clusterInfos = fetchClusterInfos();
        ClusterInfo clusterToSync = clusterInfos.stream()
                .filter(clusterInfo -> clusterId.equals(clusterInfo.id()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Cannot sync cluster. Not found cluster with id=" + clusterId));

        if (!clusterSyncLock.tryAcquire(clusterId, CLUSTER_SYNC_LOCK_TTL)) {
            throw new WebApplicationException("Cluster with id=" + clusterId + " is already being synced", 409);
        }
        try {
            clusterResourcesLoader.loadClusterResources(clusterToSync);
        } finally {
            clusterSyncLock.release(clusterId);
        }
    }

    private void syncClusterWithLock(ClusterInfo clusterInfo) {
        if (!clusterSyncLock.tryAcquire(clusterInfo.id(), CLUSTER_SYNC_LOCK_TTL)) {
            Log.infof("Cluster %s is already being synced, skipping", clusterInfo.name());
            return;
        }
        try {
            Log.info("Starting to load resources for cluster: " + clusterInfo.name());
            clusterResourcesLoader.loadClusterResources(clusterInfo);
            Log.info("Completed loading resources for cluster: " + clusterInfo.name());
        } finally {
            clusterSyncLock.release(clusterInfo.id());
        }
    }

    public List<EnvironmentDTO> getEnvironments() {
        List<ClusterInfo> clusterInfos = fetchClusterInfos();

        List<Environment> operationalEnvironments = environmentRepository.findAll();
        List<EnvironmentDTO> result = new ArrayList<>();

        for (ClusterInfo clusterInfo : clusterInfos) {
            for (CloudPassportEnvironment inventoryEnv : clusterInfo.environments()) {
                Environment operationalEnv = operationalEnvironments.stream()
                        .filter(env -> env.getName().equals(inventoryEnv.name()) &&
                                clusterInfo.id().equals(env.getClusterId()))
                        .findFirst()
                        .orElse(null);
                if (operationalEnv == null) {
                    Log.error("Inconsistent state: envgene-inventory-storage has environment: " + inventoryEnv.name() + " in cluster: " + clusterInfo.name() + " but environment-operational-service does not have it");
                    continue;
                }
                result.add(environmentMapper.toDTO(operationalEnv));
            }
        }

        return result.stream()
                .sorted(Comparator.comparing((EnvironmentDTO e) -> e.cluster().name() != null ? e.cluster().name() : "")
                        .thenComparing(EnvironmentDTO::name))
                .toList();
    }

    public List<Cluster> getClusters() {
        return clusterRepository.findAll().stream().sorted(Comparator.comparing(Cluster::getName)).toList();
    }

    public Cluster getCluster(String clusterId) {
        return clusterRepository.findById(clusterId);
    }

    public Environment getEnvironment(String environmentId) {
        return environmentRepository.findById(environmentId).orElse(null);
    }
}
