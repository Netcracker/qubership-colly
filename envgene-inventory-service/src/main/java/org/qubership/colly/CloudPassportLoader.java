package org.qubership.colly;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.qubership.colly.cloudpassport.*;
import org.qubership.colly.cloudpassport.envgen.*;
import org.qubership.colly.db.data.CmApproach;
import org.qubership.colly.db.data.EnvironmentStatus;
import org.qubership.colly.db.data.EnvironmentType;
import org.qubership.colly.projectrepo.InstanceRepository;
import org.qubership.colly.projectrepo.Project;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class CloudPassportLoader {

    public static final String ENV_DEFINITION_YML_FILENAME = "env_definition.yml";
    public static final String NAMESPACE_YML_FILENAME = "namespace.yml";
    public static final String MONITORING_TYPE_VICTORIA_DB = "VictoriaDB";
    private static final String CLOUD_PASSPORT_FOLDER = "cloud-passport";
    private static final String ENVIRONMENTS_FOLDER = "environments";
    @Inject
    GitService gitService;

    @Inject
    ParamsetService paramsetService;

    @Inject
    GitRepositoryLocks gitRepositoryLocks;

    @ConfigProperty(name = "colly.eis.cloud.passport.folder")
    String cloudPassportFolder;

    @ConfigProperty(name = "colly.eis.git.default-region", defaultValue = "cn")
    String defaultRegion;

    public List<CloudPassport> loadCloudPassports(List<Project> projects) {
        List<GitInfo> gitInfos = cloneGitRepositories(projects);
        Path dir = Paths.get(cloudPassportFolder);
        if (!dir.toFile().exists()) {
            return Collections.emptyList();
        }
        List<CloudPassport> cloudPassports = new ArrayList<>();
        for (GitInfo gitInfo : gitInfos) {
            Path environmentsDir = Paths.get(gitInfo.folderName()).resolve(ENVIRONMENTS_FOLDER);
            if (!Files.isDirectory(environmentsDir)) {
                Log.error("Environments folder not found in " + gitInfo.folderName() + ", Instance Repository=" + gitInfo.instanceRepository().url());
                continue;
            }
            try (Stream<Path> paths = Files.walk(environmentsDir)) {
                cloudPassports.addAll(paths.filter(Files::isDirectory)
                        .map(path -> path.resolve(CLOUD_PASSPORT_FOLDER))
                        .filter(Files::isDirectory)
                        .map(path -> processYamlFilesInClusterFolder(gitInfo, path, path.getParent()))
                        .filter(Objects::nonNull)
                        .toList());
            } catch (Exception e) {
                Log.errorf("Error loading CloudPassports from %s. %s", environmentsDir, e.getMessage());
            }
        }
        return cloudPassports;
    }

    private List<GitInfo> cloneGitRepositories(List<Project> projects) {
        new File(cloudPassportFolder).mkdirs();
        cleanupTmpDirectories();

        List<GitInfo> result = new ArrayList<>();
        Set<String> usedFolders = new HashSet<>();
        for (Project project : projects) {
            Map<String, String> dclFoldersByRegion = cloneDclRepositories(project);
            for (InstanceRepository instanceRepository : project.instanceRepositories()) {
                String folderName = repositoryFolderName(project, instanceRepository.url());
                if (!usedFolders.add(folderName)) {
                    Log.errorf("Instance repository %s is listed more than once in project %s, skipping duplicate",
                            instanceRepository.url(), project.name());
                    continue;
                }
                String stableFolder = cloudPassportFolder + "/" + folderName;
                String token = gitService.resolveToken(instanceRepository.token(), instanceRepository.region());
                try {
                    cloneToStableFolder(instanceRepository, token, folderName);
                } catch (IOException e) {
                    Log.errorf("Failed to replace stable directory %s, skipping repo %s",
                            stableFolder, instanceRepository.url());
                    continue;
                }
                result.add(new GitInfo(instanceRepository, stableFolder, project.id(),
                        dclFoldersByRegion.get(normalizeRegion(instanceRepository.region()))));
            }
        }
        return result;
    }

    /**
     * Clones the project's dcl repositories that have a matching instance repository region.
     * A dcl failure never stops the sync: the copy left by the previous sync, if any, keeps being read.
     *
     * @return stable clone folder by normalized region
     */
    private Map<String, String> cloneDclRepositories(Project project) {
        Set<String> instanceRegions = project.instanceRepositories().stream()
                .map(repository -> normalizeRegion(repository.region()))
                .collect(Collectors.toSet());
        Map<String, String> foldersByRegion = new HashMap<>();
        Set<String> processedRegions = new HashSet<>();
        for (InstanceRepository dclRepository : project.dclRepositories()) {
            String region = normalizeRegion(dclRepository.region());
            if (!instanceRegions.contains(region)) {
                Log.warnf("Dcl repository %s of project %s has no instance repository in region %s, skipping",
                        dclRepository.url(), project.name(), region);
                continue;
            }
            if (!processedRegions.add(region)) {
                Log.errorf("More than one dcl repository for region %s in project %s, skipping %s",
                        region, project.name(), dclRepository.url());
                continue;
            }
            String folderName = repositoryFolderName(project, dclRepository.url());
            String stableFolder = cloudPassportFolder + "/" + folderName;
            try {
                cloneToStableFolder(dclRepository, gitService.resolveToken(dclRepository.token(), region), folderName);
                foldersByRegion.put(region, stableFolder);
            } catch (IOException | RuntimeException e) {
                Log.errorf("Failed to clone dcl repository %s of project %s: %s", dclRepository.url(), project.name(), e.getMessage());
                if (Files.isDirectory(Path.of(stableFolder))) {
                    Log.warnf("Keeping previous copy of dcl repository in %s", stableFolder);
                    foldersByRegion.put(region, stableFolder);
                }
            }
        }
        return foldersByRegion;
    }

    private void cloneToStableFolder(InstanceRepository repository, String token, String folderName) throws IOException {
        String tmpFolder = cloudPassportFolder + "/" + folderName + "-tmp";
        try {
            gitService.cloneRepository(repository.url(), repository.branch(), token, new File(tmpFolder));
            replaceStableFolder(tmpFolder, cloudPassportFolder + "/" + folderName);
        } catch (IOException | RuntimeException e) {
            try {
                FileUtils.deleteDirectory(new File(tmpFolder));
            } catch (IOException ex) {
                Log.warnf("Failed to delete tmp directory %s after failed clone: %s", tmpFolder, ex.getMessage());
            }
            throw e;
        }
    }

    private String normalizeRegion(String region) {
        return StringUtils.isBlank(region) ? defaultRegion : region;
    }

    /**
     * Stable clone folder name: {@code <project name>-<first 12 hex chars of sha256(url)>}.
     * Does not depend on the order of projects / repositories, so a cluster's {@code GitInfo.folderName}
     * keeps pointing to the same repository across syncs.
     */
    static String repositoryFolderName(Project project, String repositoryUrl) {
        String projectName = StringUtils.isNotBlank(project.name()) ? project.name() : project.id();
        String safeProjectName = projectName.replaceAll("[^A-Za-z0-9._-]", "_");
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(repositoryUrl.getBytes(StandardCharsets.UTF_8));
            return safeProjectName + "-" + HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    // Held under the repository lock so a concurrent write (UpdateEnvironmentService) never sees a half-replaced folder
    private void replaceStableFolder(String tmpFolder, String stableFolder) throws IOException {
        try {
            gitRepositoryLocks.withLock(new File(stableFolder), () -> {
                try {
                    FileUtils.deleteDirectory(new File(stableFolder));
                    Files.move(Path.of(tmpFolder), Path.of(stableFolder));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private void cleanupTmpDirectories() {
        try (Stream<Path> children = Files.list(Path.of(cloudPassportFolder))) {
            children.filter(p -> p.getFileName().toString().endsWith("-tmp"))
                    .forEach(p -> {
                        try {
                            FileUtils.deleteDirectory(p.toFile());
                        } catch (IOException e) {
                            Log.warnf("Failed to delete stale tmp directory %s: %s", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            Log.warnf("Failed to list cloud passport folder for tmp cleanup %s: %s", cloudPassportFolder, e.getMessage());
        }
    }

    private CloudPassport processYamlFilesInClusterFolder(GitInfo gitInfo, Path cloudPassportFolderPath, Path clusterFolderPath) {
        Log.info("Loading Cloud Passport from " + cloudPassportFolderPath);
        String clusterName = clusterFolderPath.getFileName().toString();
        Set<CloudPassportEnvironment> environments = processEnvironmentsInClusterFolder(clusterFolderPath, gitInfo, clusterName);
        CloudPassportData cloudPassportData;
        try (Stream<Path> paths = Files.list(cloudPassportFolderPath)) {
            cloudPassportData = paths
                    .filter(path -> {
                                String fileName = path.getFileName().toString();
                                return fileName.equals(clusterName + ".yml")
                                        || fileName.equals(clusterName + ".yaml")
                                        || fileName.equals("passport.yaml")
                                        || fileName.equals("passport.yml");
                            }
                    )
                    .map(this::parseCloudPassportDataFile)
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Cloud passport data file with name (" + clusterName + ".yml|yaml, passport.yml|yaml) is not found in " + cloudPassportFolderPath));
        } catch (Exception e) {
            Log.errorf("Error loading Cloud Passport from %s. %s", cloudPassportFolderPath, e.getMessage());
            return null;
        }

        String token;
        try (Stream<Path> credsPath = Files.list(cloudPassportFolderPath)) {
            token = credsPath
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        return fileName.equals(clusterName + "-creds.yml")
                                || fileName.equals(clusterName + "-creds.yaml")
                                || fileName.equals("passport-creds.yml")
                                || fileName.equals("passport-creds.yaml");
                    })
                    .map(path -> parseTokenFromCredsFile(path, cloudPassportData))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Cloud passport creds file with name (" + clusterName + "-creds.yml|yaml, passport-creds.yml|yaml) is not found in " + cloudPassportFolderPath));

        } catch (Exception e) {
            Log.errorf("Error loading Cloud Passport from %s. %s", cloudPassportFolderPath, e.getMessage());
            return null;
        }
        CloudData cloud = cloudPassportData.cloud();
        String cloudApiHost = cloud.cloudProtocol() + "://" + cloud.cloudApiHost() + ":" + cloud.cloudApiPort();
        Log.info("Cloud API Host: " + cloudApiHost);
        Log.info("Cloud Dashboard URL: " + cloud.cloudDashboardUrl());
        CSEData cse = cloudPassportData.cse();
        String monitoringUri = null;
        if (cse != null) {
            if (StringUtils.isNotEmpty(cse.monitoringExtMonitoringQueryUrl())) {
                monitoringUri = cse.monitoringExtMonitoringQueryUrl();
                if (monitoringUri.contains("${MONITORING_NAMESPACE}") && cse.monitoringNamespace() != null) {
                    monitoringUri = monitoringUri.replace("${MONITORING_NAMESPACE}", cse.monitoringNamespace());
                }
            } else if (cse.monitoringNamespace() != null && MONITORING_TYPE_VICTORIA_DB.equals(cse.monitoringType())) {
                monitoringUri = "https://vmsingle-" + cse.monitoringNamespace() + "." + cloud.cloudPublicHost();
            }
        }
        Log.info("Monitoring URI: " + monitoringUri);
        DevopsData devops = cloudPassportData.devops();
        ArgocdData argocd = cloudPassportData.argocd();

        String argoUrl = devops != null ? devops.argocdUrl() : null;
        if (argoUrl == null && argocd != null) {
            argoUrl = argocd.argocdUrl();
        }

        String achkaUrl = devops != null ? devops.achkaUrl() : null;
        if (achkaUrl == null && argocd != null) {
            achkaUrl = argocd.achkaUrl();
        }
        achkaUrl = achkaUrl == null ? "https://ach-kubernetes-agent-devops-toolkit." + cloud.cloudPublicHost() : achkaUrl;

        Log.infof("Cloud Deployer URL: %s. Cloud Argo URL: %s, Achka URL: %s", cloud.cloudCmdbUrl(), argoUrl, achkaUrl);
        String dbaasUrl = null;
        if (cloudPassportData.dbaas() != null) dbaasUrl = cloudPassportData.dbaas().apiDBaaSAddress();
        Log.info("Cloud DBaaS URL: " + dbaasUrl);
        return new CloudPassport(clusterName, token, cloudApiHost, cloud.cloudPublicHost(), environments, monitoringUri, gitInfo,
                cloud.cloudDashboardUrl(), dbaasUrl, cloud.cloudCmdbUrl(), argoUrl, achkaUrl, cloud.region());
    }

    private Set<CloudPassportEnvironment> processEnvironmentsInClusterFolder(Path clusterFolderPath, GitInfo gitInfo, String clusterName) {
        try (Stream<Path> paths = Files.walk(clusterFolderPath)) {
            return paths.filter(Files::isDirectory)
                    .map(path -> path.resolve(ENV_DEFINITION_YML_FILENAME))
                    .filter(Files::isRegularFile)
                    .map(path -> processEnvDefinition(path, gitInfo, clusterName))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            Log.errorf("Error loading Environments from %s. %s", clusterFolderPath, e.getMessage());
        }
        return Collections.emptySet();
    }

    private CloudPassportEnvironment processEnvDefinition(Path envDevinitionPath, GitInfo gitInfo, String clusterName) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Path environmentPath = envDevinitionPath.getParent().getParent();
        List<CloudPassportNamespace> namespaces = Collections.emptyList();
        try (Stream<Path> paths = Files.walk(environmentPath)) {
            namespaces = paths.map(path -> path.resolve(NAMESPACE_YML_FILENAME))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .map(this::parseNamespaceFile)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            Log.errorf("Error loading namespaces from %s. %s", environmentPath, e.getMessage());
        }
        try (FileInputStream inputStream = new FileInputStream(envDevinitionPath.toFile())) {
            Log.info("Processing environment in folder: " + envDevinitionPath);
            EnvDefinition envDefinition = mapper.readValue(inputStream, EnvDefinition.class);
            Inventory inventory = envDefinition.inventory();
            if (inventory == null) {
                return null;
            }
            EnvDefinitionMetadata envDefinitionMetadata = envDefinition.metadata();
            String description = envDefinitionMetadata == null || envDefinitionMetadata.description() == null
                    ? inventory.description()
                    : envDefinitionMetadata.description();
            List<String> owners = envDefinitionMetadata == null || envDefinitionMetadata.owners() == null
                    ? inventory.owners() == null ? List.of() : List.of(inventory.owners())
                    : envDefinitionMetadata.owners();
            List<String> labels = envDefinitionMetadata == null || envDefinitionMetadata.labels() == null
                    ? List.of()
                    : envDefinitionMetadata.labels();
            List<String> teams = envDefinitionMetadata == null || envDefinitionMetadata.teams() == null
                    ? List.of()
                    : envDefinitionMetadata.teams();
            EnvironmentStatus environmentStatus = envDefinitionMetadata == null || envDefinitionMetadata.status() == null
                    ? EnvironmentStatus.FREE
                    : EnvironmentStatus.valueOf(envDefinitionMetadata.status());
            LocalDate expirationDate = envDefinitionMetadata == null || envDefinitionMetadata.expirationDate() == null
                    ? null
                    : LocalDate.parse(envDefinitionMetadata.expirationDate());
            EnvironmentType type = envDefinitionMetadata == null || envDefinitionMetadata.type() == null
                    ? EnvironmentType.ENVIRONMENT
                    : EnvironmentType.valueOf(envDefinitionMetadata.type());
            String role = envDefinitionMetadata == null
                    ? null
                    : envDefinitionMetadata.role();
            List<String> accessGroups = envDefinitionMetadata == null || envDefinitionMetadata.accessGroups() == null
                    ? List.of()
                    : envDefinitionMetadata.accessGroups();
            List<String> effectiveAccessGroups = envDefinitionMetadata == null || envDefinitionMetadata.effectiveAccessGroups() == null
                    ? List.of()
                    : envDefinitionMetadata.effectiveAccessGroups();
            boolean sspStandalone = envDefinitionMetadata != null && Boolean.TRUE.equals(envDefinitionMetadata.sspStandalone());
            String environmentName = StringUtils.isNotBlank(inventory.environmentName())
                    ? inventory.environmentName()
                    : environmentPath.getFileName().toString();
            CmApproach cmApproach = inventory.deployer() != null ? CmApproach.CMDB : CmApproach.NO_CMDB;
            if ("v2".equals(inventory.noCmdbVersion())) {
                cmApproach = CmApproach.NO_CMDB_V2;
            } else if ("v1".equals(inventory.noCmdbVersion())) {
                cmApproach = CmApproach.NO_CMDB;
            }

            List<Paramset> paramsets = paramsetService.parseParamsets(envDefinition.envTemplate(), envDevinitionPath.getParent());
            List<SdApplication> sdApplications = loadSolutionDescriptor(envDevinitionPath.getParent());
            String effectiveSetPath = environmentPath.resolve("effective-set").toString();
            String effectiveSetHistoryUrl = gitInfo != null && gitInfo.instanceRepository() != null
                    ? "%s/commits/%s/environments/%s/%s/effective-set".formatted(
                    gitInfo.instanceRepository().url(),
                    Objects.requireNonNullElse(gitInfo.instanceRepository().branch(), "HEAD"),
                    clusterName,
                    environmentName)
                    : null;
            return new CloudPassportEnvironment(environmentName, description, namespaces,
                    owners, labels, teams, environmentStatus, expirationDate, type, role,
                    accessGroups, effectiveAccessGroups, paramsets, sspStandalone, cmApproach, sdApplications,
                    effectiveSetPath, effectiveSetHistoryUrl,
                    resolveDclEffectiveSetPath(cmApproach, gitInfo, environmentPath, environmentName));
        } catch (IOException e) {
            Log.errorf("Error loading environment from %s. %s", environmentPath, e.getMessage());
            return null;
        }
    }

    private String resolveDclEffectiveSetPath(CmApproach cmApproach, GitInfo gitInfo, Path environmentPath, String environmentName) {
        if (cmApproach != CmApproach.NO_CMDB_V2 || gitInfo == null) {
            return null;
        }
        if (gitInfo.dclFolderName() == null) {
            Log.errorf("No dcl repository for NO_CMDB_V2 environment %s, region %s", environmentName,
                    gitInfo.instanceRepository() != null ? normalizeRegion(gitInfo.instanceRepository().region()) : null);
            return null;
        }
        Path relative = Path.of(gitInfo.folderName()).relativize(environmentPath);
        return Path.of(gitInfo.dclFolderName()).resolve(relative).resolve("effective-set").toString();
    }

    private List<SdApplication> loadSolutionDescriptor(Path inventoryDir) {
        Path deployPlanPath = findFirstExisting(inventoryDir,
                "deploy-plan.yaml", "deploy-plan.yml");
        if (deployPlanPath != null) {
            List<SdApplication> fromDeployPlan = loadDeployPlan(deployPlanPath);
            if (!fromDeployPlan.isEmpty()) {
                return fromDeployPlan;
            }
            Log.warnf("Deploy plan at %s yielded no applications — falling back to SD", deployPlanPath);
        }
        Path sdPath = findFirstExisting(inventoryDir, "solution-descriptor/sd.yaml", "solution-descriptor/sd.yml");
        if (sdPath == null) {
            return Collections.emptyList();
        }
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            SolutionDescriptor sd = mapper.readValue(sdPath.toFile(), SolutionDescriptor.class);
            if (sd.applications() == null || sd.applications().isEmpty()) {
                Log.warnf("SD at %s has no 'applications' section — skipping", sdPath);
                return Collections.emptyList();
            }
            return validateApplications(sd.applications(), sdPath);
        } catch (Exception e) {
            Log.warnf("Failed to parse SD at %s: %s", sdPath, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<SdApplication> loadDeployPlan(Path deployPlanPath) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            List<SdApplication> apps = mapper.readValue(deployPlanPath.toFile(),
                    mapper.getTypeFactory().constructCollectionType(List.class, SdApplication.class));
            if (apps == null || apps.isEmpty()) {
                return Collections.emptyList();
            }
            return validateApplications(apps, deployPlanPath);
        } catch (Exception e) {
            Log.warnf("Failed to parse deploy plan at %s: %s", deployPlanPath, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<SdApplication> validateApplications(List<SdApplication> apps, Path source) {
        for (SdApplication app : apps) {
            if (app == null || app.version() == null || app.deployPostfix() == null) {
                Log.warnf("%s has application entry with missing version or deployPostfix — discarding it", source);
                return Collections.emptyList();
            }
        }
        return List.copyOf(apps);
    }

    private static Path findFirstExisting(Path baseDir, String... relativePaths) {
        for (String relativePath : relativePaths) {
            Path candidate = baseDir.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private CloudPassportNamespace parseNamespaceFile(Path namespaceFilePath) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (FileInputStream inputStream = new FileInputStream(namespaceFilePath.toFile())) {
            Namespace namespace = mapper.readValue(inputStream, Namespace.class);
            Log.info("Processing namespace " + namespace.getName());
            String deployPostfix = namespaceFilePath.getParent().getFileName().toString();
            return new CloudPassportNamespace(namespace.getName(), deployPostfix);
        } catch (IOException e) {
            Log.errorf("Error reading namespace file: %s. %s", namespaceFilePath, e.getMessage());
            return null;
        }
    }

    String parseTokenFromCredsFile(Path path, CloudPassportData cloudPassportData) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (FileInputStream inputStream = new FileInputStream(path.toFile())) {
            JsonNode jsonNode = mapper.readTree(inputStream);
            JsonNode tokenNode = jsonNode.get(cloudPassportData.cloud().cloudDeployToken());
            if (tokenNode != null) {
                return tokenNode.findValue("secret").asText();
            }

        } catch (IOException e) {
            throw new IllegalStateException("Error during read file: " + path, e);
        }
        throw new IllegalArgumentException("Can't read cloud passport data creds from " + path);
    }

    CloudPassportData parseCloudPassportDataFile(Path filePath) {

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (FileInputStream inputStream = new FileInputStream(filePath.toFile())) {
            CloudPassportData data = mapper.readValue(inputStream, CloudPassportData.class);
            if (data != null && data.cloud() != null) {
                return data;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error during read file: " + filePath, e);
        }
        throw new IllegalArgumentException("Can't read cloud passport data from " + filePath);
    }
}
