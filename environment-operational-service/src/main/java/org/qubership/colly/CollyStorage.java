package org.qubership.colly;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;
import org.qubership.colly.cloudpassport.ClusterInfo;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.repository.ClusterRepository;
import org.qubership.colly.db.repository.EnvironmentRepository;
import org.qubership.colly.dto.EnvironmentDTO;
import org.qubership.colly.mapper.EnvironmentMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@ApplicationScoped
public class CollyStorage {

    private final ClusterResourcesLoader clusterResourcesLoader;
    private final ClusterRepository clusterRepository;
    private final EnvironmentRepository environmentRepository;
    private final EnvgeneInventoryServiceRest envgeneInventoryServiceRest;
    private final Executor executor;
    private final EnvironmentMapper environmentMapper;

    @Inject
    public CollyStorage(ClusterResourcesLoader clusterResourcesLoader,
                        ClusterRepository clusterRepository,
                        EnvironmentRepository environmentRepository,
                        EnvironmentMapper environmentMapper,
                        @RestClient EnvgeneInventoryServiceRest envgeneInventoryServiceRest,
                        @ConfigProperty(name = "colly.environment-operational-service.cluster-resource-loader.thread-pool-size") int threadPoolSize) {
        this.clusterResourcesLoader = clusterResourcesLoader;
        this.clusterRepository = clusterRepository;
        this.environmentRepository = environmentRepository;
        this.envgeneInventoryServiceRest = envgeneInventoryServiceRest;
        this.executor = Executors.newFixedThreadPool(threadPoolSize);
        this.environmentMapper = environmentMapper;
    }

    @Scheduled(cron = "{colly.environment-operational-service.cron.schedule}")
    void syncAllClusters() {
        Log.info("Task for loading resources from clusters has started");
        Date startTime = new Date();
        List<ClusterInfo> clusterInfos = envgeneInventoryServiceRest.getClusterInfos();
        List<String> clusterNames = clusterInfos.stream().map(ClusterInfo::name).toList();
        Log.info("Cloud passports loaded for clusters: " + clusterNames);

        List<CompletableFuture<Void>> futures = clusterInfos.stream()
                .map(clusterInfo -> CompletableFuture.runAsync(
                        () -> {
                            Log.info("Starting to load resources for cluster: " + clusterInfo.name());
                            clusterResourcesLoader.loadClusterResources(clusterInfo);
                            Log.info("Completed loading resources for cluster: " + clusterInfo.name());
                        }, executor))
                .toList();

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            allFutures.join(); // Wait for all to complete
        } catch (Exception e) {
            Log.error("Error occurred while loading cluster resources in parallel", e);
        }

        Date loadCompleteTime = new Date();
        long loadingDuration = loadCompleteTime.getTime() - startTime.getTime();
        Log.info("Task for loading resources from clusters has completed.");
        Log.info("Loading Duration =" + loadingDuration + " ms");
    }

    void syncCluster(String clusterId) {
        if (clusterId == null || clusterId.isEmpty()) {
            throw new IllegalArgumentException("Cluster id is null");
        }
        List<ClusterInfo> clusterInfos = envgeneInventoryServiceRest.getClusterInfos();
        ClusterInfo clusterToSync = clusterInfos.stream().filter(clusterInfo -> clusterId.equals(clusterInfo.id())).findFirst().orElse(null);
        if (clusterToSync == null) {
            throw new NotFoundException("Cannot sync cluster. Not found cluster with id=" + clusterId);
        }
        clusterResourcesLoader.loadClusterResources(clusterToSync);
    }

    public List<EnvironmentDTO> getEnvironments() {
        List<ClusterInfo> clusterInfos = envgeneInventoryServiceRest.getClusterInfos();

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
