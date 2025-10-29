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
    private final UpdateEnvironmentService updateEnvironmentService;

    @Inject
    public CollyStorage(
            ClusterRepository clusterRepository,
            CloudPassportLoader cloudPassportLoader, UpdateEnvironmentService updateEnvironmentService) {
        this.clusterRepository = clusterRepository;
        this.cloudPassportLoader = cloudPassportLoader;
        this.updateEnvironmentService = updateEnvironmentService;
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
        cluster.setGitInfo(cloudPassport.gitInfo());
        Cluster finalCluster = cluster;
        cloudPassport.environments().forEach(env -> saveEnvironmentToDatabase(env, finalCluster));
        clusterRepository.persist(finalCluster);
    }

    private void saveEnvironmentToDatabase(CloudPassportEnvironment cloudPassportEnvironment, Cluster cluster) {
        Environment environment = cluster.getEnvironments().stream().filter(env -> env.getName().equals(cloudPassportEnvironment.name())).findFirst().orElse(null);
        if (environment == null) {
            environment = new Environment(cloudPassportEnvironment.name());
            cluster.addEnvironment(environment);
            Log.info("Environment " + environment.getName() + " has been created in cache for cluster " + cluster.getName());
        }
        environment.setDescription(cloudPassportEnvironment.description());
        environment.setOwners(cloudPassportEnvironment.owners());
        environment.setLabels(cloudPassportEnvironment.labels());
        environment.setTeams(cloudPassportEnvironment.teams());
        environment.setStatus(cloudPassportEnvironment.status());
        environment.setExpirationDate(cloudPassportEnvironment.expirationDate());
        Log.info("Environment " + environment.getName() + " has been loaded from CloudPassport");
        Environment finalEnvironment = environment;
        cloudPassportEnvironment.namespaceDtos().forEach(cloudPassportNamespace -> saveNamespaceToDatabase(cloudPassportNamespace, finalEnvironment));
    }

    private void saveNamespaceToDatabase(CloudPassportNamespace cloudPassportNamespace, Environment environment) {
        Namespace namespaceInCache = environment.getNamespaces().stream().filter(namespace -> namespace.getName().equals(cloudPassportNamespace.name())).findFirst().orElse(null);
        if (namespaceInCache == null) {
            namespaceInCache = new Namespace();
            namespaceInCache.setName(cloudPassportNamespace.name());
            namespaceInCache.setUid(UUID.randomUUID().toString());
            environment.addNamespace(namespaceInCache);
            Log.info("Namespace " + namespaceInCache.getName() + " has been created in cache for environment " + environment.getName());
        }
    }

    public List<Cluster> getClusters() {
        return clusterRepository.listAll().stream().sorted(Comparator.comparing(Cluster::getName)).toList();
    }

    public Environment updateEnvironment(String clusterName, String environmentName, Environment environmentUpdate) {
        Log.info("Updating environment " + environmentName + " in cluster " + clusterName);
        Cluster cluster = clusterRepository.findByName(clusterName);
        if (cluster == null) {
            throw new IllegalArgumentException("Cluster not found: " + clusterName);
        }
        Environment existingEnv = cluster.getEnvironments().stream().filter(env -> env.getName().equals(environmentName)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + environmentName + " in cluster: " + clusterName));
        Environment updatedEnvironment = updateEnvironmentService.updateEnvironment(cluster, environmentUpdate);
        existingEnv.setOwners(updatedEnvironment.getOwners());
        existingEnv.setDescription(updatedEnvironment.getDescription());
        existingEnv.setLabels(updatedEnvironment.getLabels());
        existingEnv.setTeams(updatedEnvironment.getTeams());
        existingEnv.setStatus(updatedEnvironment.getStatus());
        existingEnv.setExpirationDate(updatedEnvironment.getExpirationDate());
        clusterRepository.persist(cluster);
        return existingEnv;
    }

}
