package org.qubership.colly;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.qubership.colly.cloudpassport.CloudPassport;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;
import org.qubership.colly.cloudpassport.CloudPassportNamespace;
import org.qubership.colly.db.ClusterRepository;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.data.Namespace;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class CollyStorage {

    private final ClusterRepository clusterRepository;
    private final CloudPassportLoader cloudPassportLoader;

    @Inject
    public CollyStorage(
            ClusterRepository clusterRepository,
            CloudPassportLoader cloudPassportLoader) {
        this.clusterRepository = clusterRepository;
        this.cloudPassportLoader = cloudPassportLoader;
    }

    @Scheduled(cron = "{colly.eis.cron.schedule}")
    void executeTask() {
        Log.info("Task for loading resources from clusters has started");
        List<CloudPassport> cloudPassports = cloudPassportLoader.loadCloudPassports();
        cloudPassports.forEach(this::saveDataToDatabase);
    }

    private void saveDataToDatabase(CloudPassport cloudPassport) {
        Cluster cluster = clusterRepository.findByName(cloudPassport.name());
        if (cluster == null) {
            cluster = new Cluster();
            cluster.setName(cloudPassport.name());
        }
        cluster.setToken(cloudPassport.token());
        cluster.setCloudApiHost(cloudPassport.cloudApiHost());
        cluster.setMonitoringUrl(cloudPassport.monitoringUrl());
        Cluster finalCluster = cluster;
        cloudPassport.environments().forEach(env -> saveEnvironmentToDatabase(env, finalCluster));
        clusterRepository.persist(cluster);
    }

    private void saveEnvironmentToDatabase(CloudPassportEnvironment cloudPassportEnvironment, Cluster cluster) {
        Environment environment = clusterRepository.findEnvironmentByNameAndCluster(cloudPassportEnvironment.name(), cluster.getName());
        if (environment == null) {
            environment = new Environment(cloudPassportEnvironment.name());
            cluster.addEnvironment(environment);
        }
        environment.setDescription(cloudPassportEnvironment.description());

        Environment finalEnvironment = environment;
        cloudPassportEnvironment.namespaceDtos().forEach(cloudPassportNamespace -> saveNamespaceToDatabase(cloudPassportNamespace, finalEnvironment, cluster));
    }

    private void saveNamespaceToDatabase(CloudPassportNamespace cloudPassportNamespace, Environment environment, Cluster cluster) {
        Namespace namespace = clusterRepository.findNamespaceByNameAndCluster(cloudPassportNamespace.name(), cluster.getName());
        if (namespace == null) {
            namespace = new Namespace();
            namespace.setName(cloudPassportNamespace.name());
            namespace.setUid(UUID.randomUUID().toString());
            environment.addNamespace(namespace);
            cluster.addNamespace(namespace);
        }
    }

    public List<Cluster> getClusters() {
        return clusterRepository.listAll().stream().sorted(Comparator.comparing(Cluster::getName)).toList();
    }

}
