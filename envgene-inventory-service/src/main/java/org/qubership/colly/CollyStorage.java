package org.qubership.colly;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.qubership.colly.cloudpassport.CloudPassport;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;
import org.qubership.colly.cloudpassport.CloudPassportNamespace;
import org.qubership.colly.cloudpassport.Paramset;
import org.qubership.colly.db.ClusterRepository;
import org.qubership.colly.db.EnvironmentRepository;
import org.qubership.colly.db.ProjectRepository;
import org.qubership.colly.db.data.*;
import org.qubership.colly.dto.PatchEnvironmentDto;
import org.qubership.colly.dto.SetUiParametersDto;
import org.qubership.colly.dto.UiParametersDto;
import org.qubership.colly.projectrepo.Project;
import org.qubership.colly.projectrepo.ProjectRepoLoader;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class CollyStorage {

    private final ClusterRepository clusterRepository;
    private final EnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;
    private final CloudPassportLoader cloudPassportLoader;
    private final UpdateEnvironmentService updateEnvironmentService;
    private final ProjectRepoLoader projectRepoLoader;
    private final ParamsetService paramsetService;
    private final AtomicBoolean syncRunning = new AtomicBoolean(false);

    CollyStorage() {
        this.clusterRepository = null;
        this.environmentRepository = null;
        this.projectRepository = null;
        this.cloudPassportLoader = null;
        this.updateEnvironmentService = null;
        this.projectRepoLoader = null;
        this.paramsetService = null;
    }

    @Inject
    public CollyStorage(
            ClusterRepository clusterRepository,
            EnvironmentRepository environmentRepository, ProjectRepository projectRepository,
            CloudPassportLoader cloudPassportLoader, UpdateEnvironmentService updateEnvironmentService,
            ProjectRepoLoader projectRepoLoader, ParamsetService paramsetService) {
        this.clusterRepository = clusterRepository;
        this.environmentRepository = environmentRepository;
        this.projectRepository = projectRepository;
        this.cloudPassportLoader = cloudPassportLoader;
        this.updateEnvironmentService = updateEnvironmentService;
        this.projectRepoLoader = projectRepoLoader;
        this.paramsetService = paramsetService;
    }

    void onStart(@Observes StartupEvent event) {
        syncAll();
    }

    @Scheduled(cron = "{colly.eis.cron.schedule}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void syncAll() {
        if (!syncRunning.compareAndSet(false, true)) {
            Log.info("syncAll is already running, skipping");
            return;
        }
        try {
            Log.info("Task for loading data from git has started");
            List<Project> projects = projectRepoLoader.loadProjects();
            projects.forEach(projectRepository::persist);
            Log.info("Projects loaded: " + projects.size());
            List<CloudPassport> cloudPassports = cloudPassportLoader.loadCloudPassports(projects);
            Log.info("Cloud passports loaded: " + cloudPassports.size());
            cloudPassports.forEach(this::saveDataToCache);
        } finally {
            syncRunning.set(false);
        }
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
        cluster.setAchkaUrl(cloudPassport.achkaUrl());

        // Persist cluster first to ensure it has an ID
        clusterRepository.persist(cluster);

        Cluster finalCluster = cluster;
        cloudPassport.environments().forEach(env -> saveEnvironmentToCache(env, finalCluster));

        // Delete environments that were removed from Git
        Set<String> currentEnvNames = cloudPassport.environments().stream()
                .map(CloudPassportEnvironment::name)
                .collect(java.util.stream.Collectors.toSet());
        environmentRepository.findByClusterId(finalCluster.getId()).stream()
                .filter(env -> !currentEnvNames.contains(env.getName()))
                .forEach(env -> {
                    Log.infof("Environment %s no longer exists in cluster %s - removing from cache", env.getName(), finalCluster.getName());
                    environmentRepository.deleteById(env.getId());
                });
    }

    private void saveEnvironmentToCache(CloudPassportEnvironment cloudPassportEnvironment, Cluster cluster) {
        // Find existing environment by name and cluster
        Environment environment = null;
        if (cluster.getId() != null) {
            environment = environmentRepository.findByNameAndClusterId(cloudPassportEnvironment.name(), cluster.getId());
        }

        if (environment == null) {
            environment = new Environment(UUID.randomUUID().toString(), cloudPassportEnvironment.name());
            Log.infof("Environment %s has been created in cache for cluster %s", environment.getName(), cluster.getName());
        }

        // Set cluster information
        final Environment finalEnvironment = environment;
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
        finalEnvironment.setParamsets(cloudPassportEnvironment.paramsets());

        Log.infof("Environment %s has been loaded from CloudPassport", finalEnvironment.getName());
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
            Log.infof("Namespace %s has been created in cache for environment %s", namespaceInCache.getName(), environment.getName());
        }
    }

    public Environment updateEnvironment(String environmentId, PatchEnvironmentDto updateDto) {
        // Find existing environment
        Log.info("Updating environment with id= " + environmentId);
        Environment existingEnv = environmentRepository.findById(environmentId);
        if (existingEnv == null) {
            throw new NotFoundException("Environment with id= " + environmentId + " not found ");
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

        // Persist changes
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


    public UiParametersDto getUiParameters(String environmentId, String namespaceName, String applicationName) {
        Environment environment = environmentRepository.findById(environmentId);
        if (environment == null) {
            throw new NotFoundException("Environment with id=" + environmentId + " not found");
        }

        List<Paramset> paramsets = environment.getParamsets();
        if (paramsets.isEmpty()) {
            return emptyUiParameters();
        }

        ParamsetService.ParamsetTarget target = paramsetService.resolveParamsetTarget(environment, namespaceName, applicationName);
        ParamsetLevel requestedLevel = target.level();
        final String dp = target.deployPostfix();
        var filtered = paramsets.stream()
                .filter(p -> p.level() == requestedLevel)
                .filter(p -> requestedLevel == ParamsetLevel.ENVIRONMENT || dp.equals(p.deployPostfix()))
                .filter(p -> requestedLevel != ParamsetLevel.APPLICATION || applicationName.equals(p.applicationName()))
                .toList();

        Map<ParamsetContext, Map<String, Object>> grouped = new EnumMap<>(ParamsetContext.class);
        for (ParamsetContext ctx : ParamsetContext.values()) {
            grouped.put(ctx, new LinkedHashMap<>());
        }
        for (Paramset filteredParamset : filtered) {
            grouped.get(filteredParamset.paramsetContext()).putAll(filteredParamset.parameters());
        }
        return new UiParametersDto(grouped);
    }

    private UiParametersDto emptyUiParameters() {
        Map<ParamsetContext, Map<String, Object>> result = new EnumMap<>(ParamsetContext.class);
        for (ParamsetContext ctx : ParamsetContext.values()) {
            result.put(ctx, Map.of());
        }
        return new UiParametersDto(result);
    }

    public void setUiParameters(String environmentId, String namespaceName, String applicationName, SetUiParametersDto setUiParametersDto) {
        Environment environment = environmentRepository.findById(environmentId);
        if (environment == null) {
            throw new NotFoundException("Environment with id=" + environmentId + " not found");
        }

        ParamsetService.ParamsetTarget target = paramsetService.resolveParamsetTarget(environment, namespaceName, applicationName);
        Cluster cluster = clusterRepository.findById(environment.getClusterId());

        List<Paramset> updatedParamsets = updateEnvironmentService.updateParamset(cluster, environment, target, applicationName, setUiParametersDto.parameters(), setUiParametersDto.commitInfo());
        environment.setParamsets(updatedParamsets);
        environmentRepository.persist(environment);
    }

}
