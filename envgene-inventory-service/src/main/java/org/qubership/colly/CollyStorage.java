package org.qubership.colly;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.qubership.colly.cloudpassport.CloudPassport;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;
import org.qubership.colly.cloudpassport.CloudPassportNamespace;
import org.qubership.colly.db.ClusterRepository;
import org.qubership.colly.db.EnvironmentRepository;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.data.Namespace;
import org.qubership.colly.dto.PatchEnvironmentDto;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class CollyStorage {

    private final ClusterRepository clusterRepository;
    private final EnvironmentRepository environmentRepository;
    private final CloudPassportLoader cloudPassportLoader;
    private final UpdateEnvironmentService updateEnvironmentService;

    @Inject
    public CollyStorage(
            ClusterRepository clusterRepository,
            EnvironmentRepository environmentRepository,
            CloudPassportLoader cloudPassportLoader, UpdateEnvironmentService updateEnvironmentService) {
        this.clusterRepository = clusterRepository;
        this.environmentRepository = environmentRepository;
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
            cluster = Cluster.builder().build();
            cluster.setName(cloudPassport.name());
        }
        cluster.setToken(cloudPassport.token());
        cluster.setCloudApiHost(cloudPassport.cloudApiHost());
        cluster.setMonitoringUrl(cloudPassport.monitoringUrl());
        cluster.setGitInfo(cloudPassport.gitInfo());

        // Persist cluster first to ensure it has an ID
        clusterRepository.persist(cluster);

        // Now save environments with cluster ID
        Cluster finalCluster = cluster;
        cloudPassport.environments().forEach(env -> saveEnvironmentToDatabase(env, finalCluster));

        // Persist cluster again after environments are added
        clusterRepository.persist(finalCluster);
    }

    private void saveEnvironmentToDatabase(CloudPassportEnvironment cloudPassportEnvironment, Cluster cluster) {
        // Find existing environment by name and cluster
        Environment environment = null;
        if (cluster.getId() != null) {
            environment = environmentRepository.findByNameAndClusterId(cloudPassportEnvironment.name(), cluster.getId());
        }

        if (environment == null) {
            environment = new Environment(UUID.randomUUID().toString(), cloudPassportEnvironment.name());
            Log.info("Environment " + environment.getName() + " has been created in cache for cluster " + cluster.getName());
        }

        // Always add/update environment in cluster for backward compatibility
        // First remove if it exists, then add the updated one
        final Environment finalEnvironment = environment;
        cluster.getEnvironments().removeIf(env -> env.getName().equals(finalEnvironment.getName()));
        cluster.getEnvironments().add(finalEnvironment);

        // Set cluster information
        if (cluster.getId() != null) {
            finalEnvironment.setClusterId(cluster.getId());
        }
        finalEnvironment.setClusterName(cluster.getName());

        // Update environment properties
        finalEnvironment.setDescription(cloudPassportEnvironment.description());
        finalEnvironment.setOwners(cloudPassportEnvironment.owners());
        finalEnvironment.setLabels(cloudPassportEnvironment.labels());
        finalEnvironment.setTeams(cloudPassportEnvironment.teams());
        finalEnvironment.setStatus(cloudPassportEnvironment.status());
        finalEnvironment.setExpirationDate(cloudPassportEnvironment.expirationDate());
        finalEnvironment.setType(cloudPassportEnvironment.type());
        finalEnvironment.setRole(cloudPassportEnvironment.role());
        finalEnvironment.setRegion(cloudPassportEnvironment.region());

        Log.info("Environment " + finalEnvironment.getName() + " has been loaded from CloudPassport");
        cloudPassportEnvironment.namespaceDtos().forEach(cloudPassportNamespace -> saveNamespaceToDatabase(cloudPassportNamespace, finalEnvironment));

        // Persist environment separately for fast access
        environmentRepository.persist(finalEnvironment);
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

    public Environment updateEnvironment(String environmentId, PatchEnvironmentDto updateDto) {
        // Find existing environment
        Log.info("Updating environment with id= " + environmentId);
        Environment existingEnv = environmentRepository.findById(environmentId);
        if (existingEnv == null) {
            throw new IllegalArgumentException("Environment with id= " + environmentId + " not found ");
        }

        // Apply partial updates to existing environment (only update provided fields)
        updateDto.description().ifPresent(existingEnv::setDescription);
        updateDto.owners().ifPresent(existingEnv::setOwners);
        updateDto.labels().ifPresent(existingEnv::setLabels);
        updateDto.teams().ifPresent(existingEnv::setTeams);
        updateDto.status().ifPresent(existingEnv::setStatus);

        // Special handling for expirationDate: empty string means clear (set to null)
        updateDto.expirationDate().ifPresent(dateStr -> {
            if (dateStr.isEmpty()) {
                existingEnv.setExpirationDate(null);
            } else {
                existingEnv.setExpirationDate(java.time.LocalDate.parse(dateStr));
            }
        });

        updateDto.type().ifPresent(existingEnv::setType);
        updateDto.role().ifPresent(existingEnv::setRole);

        // Update YAML files in Git with the updated environment
        Cluster cluster = clusterRepository.findById(existingEnv.getClusterId());
        updateEnvironmentService.updateEnvironment(cluster, existingEnv);

        // Update environment in cluster for backward compatibility
        cluster.getEnvironments().removeIf(env -> env.getName().equals(existingEnv.getName()));
        cluster.getEnvironments().add(existingEnv);

        // Persist changes
        clusterRepository.persist(cluster);
        environmentRepository.persist(existingEnv);

        return existingEnv;
    }

    public List<Environment> getEnvironments() {
        return environmentRepository.listAll();
    }

    public List<Environment> getEnvironmentsByCluster(String clusterName) {
        Cluster cluster = clusterRepository.findByName(clusterName);
        if (cluster == null) {
            throw new IllegalArgumentException("Cluster not found: " + clusterName);
        }
        return environmentRepository.findByClusterId(cluster.getId());
    }
}
