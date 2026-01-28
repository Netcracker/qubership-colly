package org.qubership.colly;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.qubership.colly.cloudpassport.CloudPassport;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;
import org.qubership.colly.cloudpassport.CloudPassportNamespace;
import org.qubership.colly.db.ClusterRepository;
import org.qubership.colly.db.EnvironmentRepository;
import org.qubership.colly.db.ProjectRepository;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.data.Namespace;
import org.qubership.colly.dto.PatchEnvironmentDto;
import org.qubership.colly.projectrepo.Project;
import org.qubership.colly.projectrepo.ProjectRepoLoader;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class CollyStorage {

    private final ClusterRepository clusterRepository;
    private final EnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;
    private final CloudPassportLoader cloudPassportLoader;
    private final UpdateEnvironmentService updateEnvironmentService;
    private final ProjectRepoLoader projectRepoLoader;

    @Inject
    public CollyStorage(
            ClusterRepository clusterRepository,
            EnvironmentRepository environmentRepository, ProjectRepository projectRepository,
            CloudPassportLoader cloudPassportLoader, UpdateEnvironmentService updateEnvironmentService, ProjectRepoLoader projectRepoLoader) {
        this.clusterRepository = clusterRepository;
        this.environmentRepository = environmentRepository;
        this.projectRepository = projectRepository;
        this.cloudPassportLoader = cloudPassportLoader;
        this.updateEnvironmentService = updateEnvironmentService;
        this.projectRepoLoader = projectRepoLoader;
    }

    @Scheduled(cron = "{colly.eis.cron.schedule}")
    void syncAll() {
        Log.info("Task for loading data from git has started");
        List<Project> projects = projectRepoLoader.loadProjects();
        projects.forEach(projectRepository::persist);
        Log.info("Projects loaded: " + projects.size());
        List<CloudPassport> cloudPassports = cloudPassportLoader.loadCloudPassports(projects);
        Log.info("Cloud passports loaded: " + cloudPassports.size());
        cloudPassports.forEach(this::saveDataToCache);
    }

    void syncProject(String projectId) {
        Project project = projectRepository.findById(projectId);
        if (project == null) {
            throw new NotFoundException("Project is not found. ID=" + projectId);
        }
        List<CloudPassport> cloudPassports = cloudPassportLoader.loadCloudPassports(List.of(project));
        Log.info("Cloud passports loaded: " + cloudPassports.size());
        cloudPassports.forEach(this::saveDataToCache);
    }

    private void saveDataToCache(CloudPassport cloudPassport) {
        Cluster cluster = clusterRepository.findByName(cloudPassport.name());
        if (cluster == null) {
            cluster = Cluster.builder().build();
            cluster.setName(cloudPassport.name());
        }
        cluster.setToken(cloudPassport.token());
        cluster.setCloudApiHost(cloudPassport.cloudApiHost());
        cluster.setCloudPublicHost(cloudPassport.cloudPublicHost());
        cluster.setMonitoringUrl(cloudPassport.monitoringUrl());
        cluster.setGitInfo(cloudPassport.gitInfo());
        cluster.setDashboardUrl(cloudPassport.dashboardUrl());
        cluster.setDbaasUrl(cloudPassport.dbaasUrl());
        cluster.setDeployerUrl(cloudPassport.deployerUrl());
        cluster.setArgoUrl(cloudPassport.argoUrl());

        // Persist cluster first to ensure it has an ID
        clusterRepository.persist(cluster);

        // Now save environments with cluster ID
        Cluster finalCluster = cluster;
        cloudPassport.environments().forEach(env -> saveEnvironmentToCache(env, finalCluster));

        // Persist cluster again after environments are added
        clusterRepository.persist(finalCluster);
    }

    private void saveEnvironmentToCache(CloudPassportEnvironment cloudPassportEnvironment, Cluster cluster) {
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
        finalEnvironment.setAccessGroups(cloudPassportEnvironment.accessGroups());
        finalEnvironment.setEffectiveAccessGroups(cloudPassportEnvironment.effectiveAccessGroups());

        Log.info("Environment " + finalEnvironment.getName() + " has been loaded from CloudPassport");
        cloudPassportEnvironment.namespaceDtos().forEach(cloudPassportNamespace -> saveNamespaceToCache(cloudPassportNamespace, finalEnvironment));

        // Persist environment separately for fast access
        environmentRepository.persist(finalEnvironment);
    }

    private void saveNamespaceToCache(CloudPassportNamespace cloudPassportNamespace, Environment environment) {
        Namespace namespaceInCache = environment.getNamespaces().stream().filter(namespace -> namespace.getName().equals(cloudPassportNamespace.name())).findFirst().orElse(null);
        if (namespaceInCache == null) {
            namespaceInCache = new Namespace();
            namespaceInCache.setName(cloudPassportNamespace.name());
            namespaceInCache.setUid(UUID.randomUUID().toString());
            namespaceInCache.setDeployPostfix(cloudPassportNamespace.deployPostfix());
            environment.addNamespace(namespaceInCache);
            Log.info("Namespace " + namespaceInCache.getName() + " has been created in cache for environment " + environment.getName());
        }
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

    public List<Project> getProjects() {
        return projectRepository.listAll();
    }

    public Project getProject(String id) {
        return projectRepository.findById(id);
    }

    public List<Cluster> getClusters(String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            return clusterRepository.listAll().stream().sorted(Comparator.comparing(Cluster::getName)).toList();
        }
        return clusterRepository.findByProjectId(projectId);
    }

    public List<Environment> getEnvironments(String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            return environmentRepository.listAll();
        }
        return clusterRepository.findByProjectId(projectId).stream()
                .flatMap(cluster -> environmentRepository.findByClusterId(cluster.getId()).stream())
                .toList();
    }

    public Environment getEnvironment(String id) {
        return environmentRepository.findById(id);
    }

    public Cluster getCluster(String id) {
        return clusterRepository.findById(id);
    }
}
