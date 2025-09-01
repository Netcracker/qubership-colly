package org.qubership.colly;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.qubership.colly.cloudpassport.CloudPassport;
import org.qubership.colly.db.ClusterRepository;
import org.qubership.colly.db.EnvironmentRepository;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.data.EnvironmentStatus;
import org.qubership.colly.db.data.EnvironmentType;

import java.time.LocalDate;
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

    @Inject
    public CollyStorage(ClusterResourcesLoader clusterResourcesLoader,
                        ClusterRepository clusterRepository,
                        EnvironmentRepository environmentRepository,
                        @RestClient EnvgeneInventoryServiceRest envgeneInventoryServiceRest,
                        @ConfigProperty(name = "colly.cluster-resource-loader.thread-pool-size") int threadPoolSize) {
        this.clusterResourcesLoader = clusterResourcesLoader;
        this.clusterRepository = clusterRepository;
        this.environmentRepository = environmentRepository;
        this.envgeneInventoryServiceRest = envgeneInventoryServiceRest;
        this.executor = Executors.newFixedThreadPool(threadPoolSize);
    }

    @Scheduled(cron = "{cron.schedule}")
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

    public List<Environment> getEnvironments() {
        return environmentRepository.findAll().stream()
                .sorted(Comparator.comparing((Environment e) -> e.getCluster().getName())
                        .thenComparing(Environment::getName))
                .toList();
    }

    public List<Cluster> getClusters() {
        return clusterRepository.findAll().stream().sorted(Comparator.comparing(Cluster::getName)).toList();
    }


    @Transactional
    public void saveEnvironment(String id, String name, String owner, String description, String status, List<String> labels, String type, String team, LocalDate expirationDate) {
        Environment environment = environmentRepository.findById(Long.valueOf(id));
        if (environment == null) {
            throw new IllegalArgumentException("Environment with id " + id + " not found");
        }
        Log.info("Saving environment with id " + id + " name " + name + " owner " + owner + " description " + description + " status " + status + " labels " + labels + " date " + expirationDate);
        environment.setOwner(owner);
        environment.setDescription(description);
        environment.setStatus(EnvironmentStatus.fromString(status));
        environment.setType(EnvironmentType.fromString(type));
        environment.setTeam(team);
        environment.setExpirationDate(expirationDate);
        environment.setLabels(labels);
        environmentRepository.persist(environment);
    }

    @Transactional
    public void saveCluster(String clusterName, String description) {
        Cluster cluster = clusterRepository.findByName(clusterName);
        if (cluster == null) {
            throw new IllegalArgumentException("Cluster with name " + clusterName + " not found");
        }
        Log.info("Saving cluster with name " + clusterName + " description " + description);
        cluster.setDescription(description);

        clusterRepository.persist(cluster);
    }

    @Transactional
    public void deleteEnvironment(String id) {
        boolean found = environmentRepository.deleteById(Long.valueOf(id));
        if (!found) {
            throw new IllegalArgumentException("Environment with id " + id + " not found");
        }
    }
}
