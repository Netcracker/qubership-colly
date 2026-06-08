package org.qubership.colly;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.qubership.colly.cloudpassport.*;
import org.qubership.colly.db.ClusterRepository;
import org.qubership.colly.db.EnvironmentRepository;
import org.qubership.colly.db.ProjectRepository;
import org.qubership.colly.db.data.*;
import org.qubership.colly.dto.EffectiveSetResponseDto;
import org.qubership.colly.dto.PatchEnvironmentDto;
import org.qubership.colly.dto.SetUiParametersDto;
import org.qubership.colly.dto.UiParametersDto;
import org.qubership.colly.projectrepo.Project;
import org.qubership.colly.projectrepo.ProjectRepoLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class CollyStorage {

    private static final Set<String> VALID_CONTEXTS = Set.of("deployment", "runtime", "pipeline");
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private final ConcurrentHashMap<String, Map<String, Object>> effectiveSetCache = new ConcurrentHashMap<>();

    private final ClusterRepository clusterRepository;
    private final EnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;
    private final CloudPassportLoader cloudPassportLoader;
    private final UpdateEnvironmentService updateEnvironmentService;
    private final ProjectRepoLoader projectRepoLoader;
    private final ParamsetService paramsetService;

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

    @Scheduled(cron = "{colly.eis.cron.schedule}")
    void syncAll() {
        Log.info("Task for loading data from git has started");
        effectiveSetCache.clear();
        List<Project> projects = projectRepoLoader.loadProjects();
        removeDeletedProjects(projects);
        projects.forEach(projectRepository::persist);
        Log.info("Projects loaded: " + projects.size());
        List<CloudPassport> cloudPassports = cloudPassportLoader.loadCloudPassports(projects);
        Log.info("Cloud passports loaded: " + cloudPassports.size());
        removeDeletedClusters(cloudPassports);
        cloudPassports.forEach(this::saveDataToCache);
    }

    private void removeDeletedClusters(List<CloudPassport> currentCloudPassports) {
        Set<String> currentClusterNames = currentCloudPassports.stream()
                .map(CloudPassport::name)
                .collect(Collectors.toSet());
        clusterRepository.listAll().stream()
                .filter(cached -> !currentClusterNames.contains(cached.getName()))
                .forEach(deleted -> {
                    Log.infof("Cluster %s no longer exists in git - removing from cache", deleted.getName());
                    environmentRepository.findByClusterId(deleted.getId())
                            .forEach(env -> environmentRepository.deleteById(env.getId()));
                    clusterRepository.deleteById(deleted.getId());
                });
    }

    private void removeDeletedProjects(List<Project> currentProjects) {
        Set<String> currentProjectIds = currentProjects.stream()
                .map(Project::id)
                .collect(Collectors.toSet());
        projectRepository.listAll().stream()
                .filter(cached -> !currentProjectIds.contains(cached.id()))
                .forEach(deleted -> {
                    Log.infof("Project %s no longer exists in git - removing from cache", deleted.name());
                    clusterRepository.findByProjectId(deleted.id()).forEach(cluster -> {
                        environmentRepository.findByClusterId(cluster.getId())
                                .forEach(env -> environmentRepository.deleteById(env.getId()));
                        clusterRepository.deleteById(cluster.getId());
                    });
                    projectRepository.deleteById(deleted.id());
                });
    }

    void syncProject(String projectId) {
        Project project = projectRepository.findById(projectId);
        if (project == null) {
            throw new NotFoundException("Project is not found. ID=" + projectId);
        }
        effectiveSetCache.clear();
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
        cluster.setRegion(cloudPassport.region());

        // Persist cluster first to ensure it has an ID
        clusterRepository.persist(cluster);

        Cluster finalCluster = cluster;
        cloudPassport.environments().forEach(env -> saveEnvironmentToCache(env, finalCluster));

        // Delete environments that were removed from Git
        Set<String> currentEnvNames = cloudPassport.environments().stream()
                .map(CloudPassportEnvironment::name)
                .collect(Collectors.toSet());
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
        finalEnvironment.setAccessGroups(cloudPassportEnvironment.accessGroups());
        finalEnvironment.setEffectiveAccessGroups(cloudPassportEnvironment.effectiveAccessGroups());
        finalEnvironment.setParamsets(cloudPassportEnvironment.paramsets());
        finalEnvironment.setSdApplications(cloudPassportEnvironment.sdApplications());
        finalEnvironment.setEffectiveSetPath(cloudPassportEnvironment.effectiveSetPath());
        finalEnvironment.setSspStandalone(cloudPassportEnvironment.sspStandalone());
        finalEnvironment.setCmApproach(cloudPassportEnvironment.cmApproach());

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

    public EffectiveSetResponseDto getEffectiveSet(String environmentId, String context,
                                                   String namespaceName, String applicationName, Map<String, Object> requestParameters) {

        if (!VALID_CONTEXTS.contains(context)) {
            throw new BadRequestException("context must be one of: deployment, runtime, pipeline");
        }

        boolean isPipeline = "pipeline".equals(context);
        if (isPipeline) {
            if (namespaceName != null || applicationName != null) {
                throw new BadRequestException("namespaceName and applicationName must not be present for context=pipeline");
            }
        } else {
            if (namespaceName == null || namespaceName.isBlank()) {
                throw new BadRequestException("namespaceName is required for context=" + context);
            }
            if (applicationName == null || applicationName.isBlank()) {
                throw new BadRequestException("applicationName is required for context=" + context);
            }
        }

        Environment environment = environmentRepository.findById(environmentId);
        if (environment == null) {
            throw new NotFoundException("Environment with id=" + environmentId + " not found");
        }

        String deployPostfix = null;
        if (!isPipeline) {
            deployPostfix = environment.getNamespaces().stream()
                    .filter(ns -> namespaceName.equals(ns.getName()))
                    .map(Namespace::getDeployPostfix)
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException(
                            "Namespace '" + namespaceName + "' not found in environment " + environmentId));

            String dp = deployPostfix;
            boolean appExists = environment.getSdApplications().stream()
                    .anyMatch(app -> dp.equals(app.deployPostfix())
                            && applicationName.equals(app.version().contains(":") ? app.version().split(":")[0] : app.version()));
            if (!appExists) {
                throw new NotFoundException(
                        "Application '" + applicationName + "' not found for namespace '" + namespaceName + "'");
            }
        }

        Path filePath = resolveEffectiveSetFilePath(environment.getEffectiveSetPath(), context, deployPostfix, applicationName);
        String cacheKey = environmentId + ":" + context + ":" + deployPostfix + ":" + applicationName;

        Map<String, Object> cached = effectiveSetCache.computeIfAbsent(cacheKey,
                k -> readEffectiveSetFile(filePath, context));

        Map<String, Object> merged = deepCopy(cached);
        if (requestParameters != null) {
            mergeInto(merged, requestParameters);
        }

        return new EffectiveSetResponseDto(context, environmentId, namespaceName, applicationName, wrapMap(merged));
    }

    private static Path resolveEffectiveSetFilePath(String root, String context, String deployPostfix, String applicationName) {
        Path r = Path.of(root);
        return switch (context) {
            case "deployment" -> r.resolve("deployment").resolve(deployPostfix).resolve(applicationName)
                    .resolve("values").resolve("deployment-parameters.yaml");
            case "runtime" -> r.resolve("runtime").resolve(deployPostfix).resolve(applicationName)
                    .resolve("parameters.yaml");
            case "pipeline" -> r.resolve("pipeline").resolve("parameters.yaml");
            default -> throw new IllegalStateException("Unexpected context: " + context);
        };
    }

    private static Map<String, Object> readEffectiveSetFile(Path filePath, String context) {
        if (!Files.isRegularFile(filePath)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = YAML_MAPPER.readValue(filePath.toFile(), Map.class);
            return "deployment".equals(context) ? filterGlobalAliases(raw) : raw;
        } catch (IOException e) {
            Log.errorf("Failed to read effective-set file %s: %s", filePath, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static Map<String, Object> filterGlobalAliases(Map<String, Object> raw) {
        Object globalVal = raw.get("global");
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if ("global".equals(entry.getKey())) continue;
            if (globalVal != null && entry.getValue() == globalVal) continue;
            filtered.put(entry.getKey(), entry.getValue());
        }
        return filtered;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>(source.size());
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object val = entry.getValue();
            copy.put(entry.getKey(), val instanceof Map ? deepCopy((Map<String, Object>) val) : val);
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static void mergeInto(Map<String, Object> target, Map<String, Object> overlay) {
        for (Map.Entry<String, Object> entry : overlay.entrySet()) {
            String key = entry.getKey();
            Object overlayVal = entry.getValue();
            if (overlayVal instanceof Map && target.get(key) instanceof Map) {
                mergeInto((Map<String, Object>) target.get(key), (Map<String, Object>) overlayVal);
            } else {
                target.put(key, overlayVal);
            }
        }
    }

    private static Map<String, Object> wrapMap(Map<String, Object> params) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            result.put(entry.getKey(), wrapValue(entry.getValue()));
        }
        return result;
    }

    private static Map<String, Object> wrapValue(Object value) {
        if (value instanceof Map<?, ?>) {
            return Map.of("_type", "container", "_data", wrapMap((Map<String, Object>) value));
        }
        Map<String, Object> leafData = new LinkedHashMap<>();
        leafData.put("value", value);
        leafData.put("state", "ui_override_untouched");
        leafData.put("originalValue", value);
        return Map.of("_type", "leaf", "_data", leafData);
    }

    public Cluster getCluster(String id) {
        return clusterRepository.findById(id);
    }


    public List<String> getApplications(String environmentId, String namespaceName) {
        Environment environment = environmentRepository.findById(environmentId);
        if (environment == null) {
            throw new NotFoundException("Environment with id=" + environmentId + " not found");
        }

        String deployPostfix = environment.getNamespaces().stream()
                .filter(ns -> ns.getName().equals(namespaceName))
                .map(Namespace::getDeployPostfix)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Namespace '" + namespaceName + "' not found in environment id=" + environmentId));

        List<SdApplication> apps = environment.getSdApplications();
        if (apps.isEmpty()) {
            Log.warnf("No SD data for environment %s — returning empty list", environmentId);
            return Collections.emptyList();
        }

        return apps.stream()
                .filter(app -> deployPostfix.equals(app.deployPostfix()))
                .map(app -> app.version().contains(":") ? app.version().split(":")[0] : app.version())
                .distinct()
                .toList();
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
