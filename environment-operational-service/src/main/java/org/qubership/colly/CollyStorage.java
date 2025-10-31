package org.qubership.colly;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.qubership.colly.cloudpassport.CloudPassport;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;
import org.qubership.colly.db.data.*;
import org.qubership.colly.db.repository.ClusterRepository;
import org.qubership.colly.db.repository.EnvironmentRepository;
import org.qubership.colly.dto.EnvironmentDTO;
import org.qubership.colly.mapper.EnvironmentMapper;

import java.time.LocalDate;
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
    void executeTask() {
        Log.info("Task for loading resources from clusters has started");
        Date startTime = new Date();
        List<CloudPassport> cloudPassports = envgeneInventoryServiceRest.getCloudPassports();
        List<String> clusterNames = cloudPassports.stream().map(CloudPassport::name).toList();
        Log.info("Cloud passports loaded for clusters: " + clusterNames);

        List<CompletableFuture<Void>> futures = cloudPassports.stream()
                .map(cloudPassport -> CompletableFuture.runAsync(
                        () -> {
                            Log.info("Starting to load resources for cluster: " + cloudPassport.name());
                            clusterResourcesLoader.loadClusterResources(cloudPassport);
                            Log.info("Completed loading resources for cluster: " + cloudPassport.name());
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

    public List<EnvironmentDTO> getEnvironments() {
        List<CloudPassport> cloudPassports = envgeneInventoryServiceRest.getCloudPassports();

        List<Environment> operationalEnvironments = environmentRepository.findAll();
        List<EnvironmentDTO> result = new ArrayList<>();

        for (CloudPassport cloudPassport : cloudPassports) {
            for (CloudPassportEnvironment inventoryEnv : cloudPassport.environments()) {
                Environment operationalEnv = operationalEnvironments.stream()
                        .filter(env -> env.getName().equals(inventoryEnv.name()) &&
                                cloudPassport.name().equals(env.getClusterId()))
                        .findFirst()
                        .orElse(null);
                if (operationalEnv == null) {
                    Log.error("Inconsistent state: envgene-inventory-storage has environment: " + inventoryEnv.name() + " in cluster: " + cloudPassport.name() + " but environment-operational-service does not have it");
                    continue;
                }
                result.add(environmentMapper.toDTO(operationalEnv, inventoryEnv));
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


    //@Transactional - removed for Redis
    public void saveEnvironment(String id, String name, List<String> owner, String description, String status,
                                List<String> labels, String type, List<String> teams, LocalDate expirationDate, String role) {
        Environment environment = environmentRepository.findById(id).orElse(null);
        if (environment == null) {
            throw new IllegalArgumentException("Environment with id " + id + " not found");
        }
        Log.info("Saving environment with id " + id + " name " + name + " owners " + owner + " description " + description + " status " + status + " labels " + labels + " date " + expirationDate);
        envgeneInventoryServiceRest.updateEnvironment(environment.getClusterId(), environment.getName(),
                new CloudPassportEnvironment(environment.getName(), description, null, owner, labels, teams, status, expirationDate, type, role));
        Log.info("Successfully updated environment in inventory service: " + environment.getName());
    }

    //@Transactional - removed for Redis
    public void saveCluster(String clusterName, String description) {
        Cluster cluster = clusterRepository.findByName(clusterName).orElse(null);
        if (cluster == null) {
            throw new IllegalArgumentException("Cluster with name " + clusterName + " not found");
        }
        Log.info("Saving cluster with name " + clusterName + " description " + description);
        cluster.setDescription(description);

        clusterRepository.save(cluster);
    }

    //@Transactional - removed for Redis
    //todo update inventory service first then remove from cache
    public void deleteEnvironment(String id) {
        if (environmentRepository.findById(id).isEmpty()) {
            throw new IllegalArgumentException("Environment with id " + id + " not found");
        }
        environmentRepository.delete(id);
    }
}
