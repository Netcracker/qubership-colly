package org.qubership.colly;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.qubership.colly.db.Cluster;
import org.qubership.colly.db.Environment;
import org.qubership.colly.storage.ClusterRepository;
import org.qubership.colly.storage.EnvironmentRepository;

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

    @Scheduled(cron = "{cron.schedule}")
    @Transactional
    void executeTask() {
        Log.info("Task for loading resources from clusters has started");
        Date startTime = new Date();
        clusterResourcesLoader.loadClusters();
        Date loadCompleteTime = new Date();
        long loadingDuration = loadCompleteTime.getTime() - startTime.getTime();
        Log.info("Task for loading resources from clusters has completed.");
        Log.info("Loading Duration =" + loadingDuration + " ms");
    }

    public List<Environment> getEnvironments() {
        return environmentRepository.findAll().list();
    }

    public List<Cluster> getClusters() {
        return clusterRepository.findAll().list();
    }

    public List<Cluster> getClustersFromDb() {
        return clusterRepository.findAll().list();
    }
}
