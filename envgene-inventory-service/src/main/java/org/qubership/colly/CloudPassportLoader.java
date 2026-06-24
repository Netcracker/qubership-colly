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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    @ConfigProperty(name = "colly.eis.cloud.passport.folder")
    String cloudPassportFolder;

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
                Log.error("Error loading CloudPassports from " + environmentsDir, e);
            }
        }
        return cloudPassports;
    }

    private List<GitInfo> cloneGitRepositories(List<Project> projects) {
        new File(cloudPassportFolder).mkdirs();
        cleanupTmpDirectories();

        List<GitInfo> result = new ArrayList<>();
        int index = 1;
        for (Project project : projects) {
            for (InstanceRepository instanceRepository : project.instanceRepositories()) {
                String tmpFolder = cloudPassportFolder + "/" + index + "-tmp";
                String stableFolder = cloudPassportFolder + "/" + index;
                String token = gitService.resolveToken(instanceRepository.token(), instanceRepository.region());
                gitService.cloneRepository(instanceRepository.url(), instanceRepository.branch(), token, new File(tmpFolder));
                try {
                    FileUtils.deleteDirectory(new File(stableFolder));
                    Files.move(Path.of(tmpFolder), Path.of(stableFolder));
                } catch (IOException e) {
                    Log.errorf("Failed to replace stable directory %s, skipping repo %s",
                            stableFolder, instanceRepository.url());
                    try {
                        FileUtils.deleteDirectory(new File(tmpFolder));
                    } catch (IOException ignored) {
                    }
                    index++;
                    continue;
                }
                result.add(new GitInfo(instanceRepository, stableFolder, project.id()));
                index++;
            }
        }
        return result;
    }

    private void cleanupTmpDirectories() {
        try (Stream<Path> children = Files.list(Path.of(cloudPassportFolder))) {
            children.filter(p -> p.getFileName().toString().endsWith("-tmp"))
                    .forEach(p -> {
                        try {
                            FileUtils.deleteDirectory(p.toFile());
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private CloudPassport processYamlFilesInClusterFolder(GitInfo gitInfo, Path cloudPassportFolderPath, Path clusterFolderPath) {
        Log.info("Loading Cloud Passport from " + cloudPassportFolderPath);
        String clusterName = clusterFolderPath.getFileName().toString();
        Set<CloudPassportEnvironment> environments = processEnvironmentsInClusterFolder(clusterFolderPath);
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
            Log.error("Error loading Cloud Passport from " + cloudPassportFolderPath, e);
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
            Log.error("Error loading Cloud Passport from " + cloudPassportFolderPath, e);
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
        String argoUrl = null;
        String achkaUrl = "https://ach-kubernetes-agent-devops-toolkit." + cloud.cloudPublicHost();
        DevopsData devops = cloudPassportData.devops();
        if (Objects.nonNull(devops)) {
            argoUrl = devops.argocdUrl();
            if (devops.achkaUrl() != null) {
                achkaUrl = devops.achkaUrl();
            }
        }
        Log.infof("Cloud Deployer URL: %s. Cloud Argo URL: %s, Achka URL: %s", cloud.cloudCmdbUrl(), argoUrl, achkaUrl);
        String dbaasUrl = null;
        if (cloudPassportData.dbaas() != null) dbaasUrl = cloudPassportData.dbaas().apiDBaaSAddress();
        Log.info("Cloud DBaaS URL: " + dbaasUrl);
        return new CloudPassport(clusterName, token, cloudApiHost, cloud.cloudPublicHost(), environments, monitoringUri, gitInfo,
                cloud.cloudDashboardUrl(), dbaasUrl, cloud.cloudCmdbUrl(), argoUrl, achkaUrl, cloud.region());
    }

    private Set<CloudPassportEnvironment> processEnvironmentsInClusterFolder(Path clusterFolderPath) {
        try (Stream<Path> paths = Files.walk(clusterFolderPath)) {
            return paths.filter(Files::isDirectory)
                    .map(path -> path.resolve(ENV_DEFINITION_YML_FILENAME))
                    .filter(Files::isRegularFile)
                    .map(this::processEnvDefinition)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            Log.error("Error loading Environments from " + clusterFolderPath, e);
        }
        return Collections.emptySet();
    }

    private CloudPassportEnvironment processEnvDefinition(Path envDevinitionPath) {
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
            Log.error("Error loading namespaces from " + environmentPath, e);
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
            List<Paramset> paramsets = paramsetService.parseParamsets(envDefinition.envTemplate(), envDevinitionPath.getParent());
            List<SdApplication> sdApplications = loadSolutionDescriptor(envDevinitionPath.getParent());
            String effectiveSetPath = environmentPath.resolve("effective-set").toString();
            return new CloudPassportEnvironment(environmentName, description, namespaces,
                    owners, labels, teams, environmentStatus, expirationDate, type, role,
                    accessGroups, effectiveAccessGroups, paramsets, sspStandalone, cmApproach, sdApplications,
                    effectiveSetPath);
        } catch (IOException e) {
            Log.error("Error loading environment from " + environmentPath, e);
            return null;
        }
    }

    private List<SdApplication> loadSolutionDescriptor(Path inventoryDir) {
        Path sdPath = inventoryDir.resolve("solution-descriptor/sd.yaml");
        if (!Files.isRegularFile(sdPath)) {
            sdPath = inventoryDir.resolve("solution-descriptor/sd.yml");
        }
        if (!Files.isRegularFile(sdPath)) {
            return Collections.emptyList();
        }
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            SolutionDescriptor sd = mapper.readValue(sdPath.toFile(), SolutionDescriptor.class);
            if (sd.applications() == null || sd.applications().isEmpty()) {
                Log.warnf("SD at %s has no 'applications' section — skipping", sdPath);
                return Collections.emptyList();
            }
            List<SdApplication> result = new ArrayList<>();
            for (SdApplication app : sd.applications()) {
                if (app.version() == null || app.deployPostfix() == null) {
                    Log.warnf("SD at %s has application entry with missing version or deployPostfix — discarding SD", sdPath);
                    return Collections.emptyList();
                }
                result.add(app);
            }
            return result;
        } catch (Exception e) {
            Log.warnf("Failed to parse SD at %s: %s", sdPath, e.getMessage());
            return Collections.emptyList();
        }
    }

    private CloudPassportNamespace parseNamespaceFile(Path namespaceFilePath) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (FileInputStream inputStream = new FileInputStream(namespaceFilePath.toFile())) {
            Namespace namespace = mapper.readValue(inputStream, Namespace.class);
            Log.info("Processing namespace " + namespace.getName());
            String deployPostfix = namespaceFilePath.getParent().getFileName().toString();
            return new CloudPassportNamespace(namespace.getName(), deployPostfix);
        } catch (IOException e) {
            Log.error("Error reading namespace file: " + namespaceFilePath, e);
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
