package org.qubership.colly;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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

@ApplicationScoped
public class CollyStorage {

    @Inject
    ClusterResourcesLoader clusterResourcesLoader;

    @Inject
    ClusterRepository clusterRepository;

    @Inject
    EnvironmentRepository environmentRepository;

    @Inject
    CloudPassportLoader cloudPassportLoader;

    @Scheduled(cron = "{cron.schedule}")
    void executeTask() {
        Log.info("Task for loading resources from clusters has started");
        Date startTime = new Date();
        List<CloudPassport> cloudPassports = cloudPassportLoader.loadCloudPassports();
        cloudPassports.forEach(cloudPassport -> clusterResourcesLoader.loadClusterResources(cloudPassport));
        List<String> clusterNames = cloudPassports.stream().map(CloudPassport::name).toList();
        Log.info("Cloud passports loaded for clusters: " + clusterNames);

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
        cluster.description = description;

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
