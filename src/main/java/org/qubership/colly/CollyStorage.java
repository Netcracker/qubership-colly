package org.qubership.colly;

import io.kubernetes.client.util.KubeConfig;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.qubership.colly.data.CloudPassport;
import org.qubership.colly.db.Cluster;
import org.qubership.colly.db.Environment;
import org.qubership.colly.storage.ClusterRepository;
import org.qubership.colly.storage.EnvironmentRepository;

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
    KubeConfigLoader kubeConfigLoader;

    @Inject
    CloudPassportLoader cloudPassportLoader;

    @Scheduled(cron = "{cron.schedule}")
    void executeTask() {
        Log.info("Task for loading resources from clusters has started");
        Date startTime = new Date();
        List<CloudPassport> cloudPassports = cloudPassportLoader.loadCloudPassports();
        cloudPassports.forEach(cloudPassport -> clusterResourcesLoader.loadClusterResources(cloudPassport));
        List<String> clusterNames = cloudPassports.stream().map(CloudPassport::name).toList();
        List<KubeConfig> kubeConfigs = kubeConfigLoader.loadKubeConfigs();
        kubeConfigs.stream()
                .filter(kubeConfig -> clusterNames.contains(ClusterResourcesLoader.parseClusterName(kubeConfig)))
                .forEach(kubeConfig -> clusterResourcesLoader.loadClusterResources(kubeConfig));

        Date loadCompleteTime = new Date();
        long loadingDuration = loadCompleteTime.getTime() - startTime.getTime();
        Log.info("Task for loading resources from clusters has completed.");
        Log.info("Loading Duration =" + loadingDuration + " ms");
    }

    public List<Environment> getEnvironments() {
        return environmentRepository.findAll().stream().sorted(Comparator.comparing(o -> o.name)).toList();
    }

    public List<Cluster> getClusters() {
        return clusterRepository.findAll().list();
    }


    @Transactional
    public void saveEnvironment(String id, String name, String owner, String description) {
        Environment environment = environmentRepository.findById(Long.valueOf(id));
        if (environment == null) {
            throw new RuntimeException("Environment with id " + id + " not found");
        }
        Log.info("Saving environment with id " + id + " name " + name + " owner " + owner + " description " + description);
        environment.name = name;
        environment.owner = owner;
        environment.description = description;
        environmentRepository.persist(environment);
    }
}
