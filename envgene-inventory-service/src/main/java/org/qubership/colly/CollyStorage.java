package org.qubership.colly;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.qubership.colly.cloudpassport.CloudPassport;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;
import org.qubership.colly.cloudpassport.CloudPassportNamespace;
import org.qubership.colly.db.ClusterRepository;
import org.qubership.colly.db.EnvironmentRepository;
import org.qubership.colly.db.NamespaceRepository;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.data.Namespace;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class CollyStorage {

    private final ClusterRepository clusterRepository;
    private final EnvironmentRepository environmentRepository;
    private final NamespaceRepository namespaceRepository;
    private final CloudPassportLoader cloudPassportLoader;

    @Inject
    public CollyStorage(
            ClusterRepository clusterRepository,
            EnvironmentRepository environmentRepository,
            NamespaceRepository namespaceRepository,
            CloudPassportLoader cloudPassportLoader) {
        this.clusterRepository = clusterRepository;
        this.environmentRepository = environmentRepository;
        this.namespaceRepository = namespaceRepository;
        this.cloudPassportLoader = cloudPassportLoader;
    }

    @Scheduled(cron = "{colly.eis.cron.schedule}")
    @Transactional
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
        cluster.persist();

        Cluster finalCluster = cluster;
        cloudPassport.environments().forEach(env -> saveEnvironmentToDatabase(env, finalCluster));
    }

    private void saveEnvironmentToDatabase(CloudPassportEnvironment cloudPassportEnvironment, Cluster cluster) {
        Environment environment = environmentRepository.findByNameAndCluster(cloudPassportEnvironment.name(), cluster.getName());
        if (environment == null) {
            environment = new Environment(cloudPassportEnvironment.name());
        }
        environment.setDescription(cloudPassportEnvironment.description());
        environment.setCluster(cluster);
        environment.persist();

        Environment finalEnvironment = environment;
        cloudPassportEnvironment.namespaceDtos().forEach(cloudPassportNamespace -> saveNamespaceToDatabase(cloudPassportNamespace, finalEnvironment));
        environment.persist();
    }

    private void saveNamespaceToDatabase(CloudPassportNamespace cloudPassportNamespace, Environment environment) {
        Namespace namespace = namespaceRepository.findByNameAndCluster(environment.getName(), cloudPassportNamespace.name());
        if (namespace == null) {
            namespace = new Namespace();
            namespace.setName(cloudPassportNamespace.name());
            namespace.setEnvironment(environment);
            namespace.setUid(UUID.randomUUID().toString());
        }
        namespace.setCluster(environment.getCluster());
        namespace.persist();
        environment.addNamespace(namespace);
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

}
