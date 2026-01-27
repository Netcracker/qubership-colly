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
import org.qubership.colly.cloudpassport.CloudPassport;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;
import org.qubership.colly.cloudpassport.CloudPassportNamespace;
import org.qubership.colly.cloudpassport.GitInfo;
import org.qubership.colly.cloudpassport.envgen.*;
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
    @Inject
    GitService gitService;

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
            try (Stream<Path> paths = Files.walk(Paths.get(gitInfo.folderName()))) {
                cloudPassports.addAll(paths.filter(Files::isDirectory)
                        .map(path -> path.resolve(CLOUD_PASSPORT_FOLDER))
                        .filter(Files::isDirectory)
                        .map(path -> processYamlFilesInClusterFolder(gitInfo, path, path.getParent()))
                        .filter(Objects::nonNull)
                        .toList());
            } catch (Exception e) {
                Log.error("Error loading CloudPassports from " + dir, e);
            }
        }
        return cloudPassports;
    }

    private List<GitInfo> cloneGitRepositories(List<Project> projects) {

        File directory = new File(cloudPassportFolder);

        try {
            if (directory.exists()) {
                FileUtils.deleteDirectory(directory);
            }
        } catch (IOException e) {
            Log.error("Impossible to start git cloning. Failed to clean directory: " + cloudPassportFolder, e);
            return Collections.emptyList();
        }

        List<GitInfo> result = new ArrayList<>();
        int index = 1;
        for (Project project : projects) {

            for (InstanceRepository instanceRepository : project.instanceRepositories()) {
                String folderNameToClone = cloudPassportFolder + "/" + index;
                gitService.cloneRepository(instanceRepository.url(), instanceRepository.branch(), instanceRepository.token(), new File(folderNameToClone));
                result.add(new GitInfo(instanceRepository, folderNameToClone, project.id()));
                index++;
            }
        }
        return result;
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
                monitoringUri = "http://vmsingle-k8s." + cse.monitoringNamespace() + ":8429";
            }
        }
        Log.info("Monitoring URI: " + monitoringUri);
        String argoUrl = null;
        if (Objects.nonNull(cloudPassportData.argocd())) {
            argoUrl = cloudPassportData.argocd().argocdUrl();
        }
        Log.infof("Cloud Deployer URL: %s. Cloud Argo URL: %s", cloud.cloudCmdbUrl(), argoUrl);
        String dbaasUrl = null;
        if (cloudPassportData.dbaas() != null) dbaasUrl = cloudPassportData.dbaas().apiDBaaSAddress();
        Log.info("Cloud DBaaS URL: " + dbaasUrl);
        return new CloudPassport(clusterName, token, cloudApiHost, environments, monitoringUri, gitInfo,
                cloud.cloudDashboardUrl(), dbaasUrl, cloud.cloudCmdbUrl(), argoUrl);
    }

    private Set<CloudPassportEnvironment> processEnvironmentsInClusterFolder(Path clusterFolderPath) {
        try (Stream<Path> paths = Files.walk(clusterFolderPath)) {
            return paths.filter(Files::isDirectory)
                    .map(path -> path.resolve(ENV_DEFINITION_YML_FILENAME))
                    .filter(Files::isRegularFile)
                    .map(this::processEnvDefinition)
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
                    .toList();
        } catch (IOException e) {
            Log.error("Error loading environment name from " + environmentPath, e);
        }
        try (FileInputStream inputStream = new FileInputStream(envDevinitionPath.toFile())) {
            EnvDefinition envDefinition = mapper.readValue(inputStream, EnvDefinition.class);
            Inventory inventory = envDefinition.getInventory();
            Log.info("Processing environment " + inventory.getEnvironmentName());
            InventoryMetadata inventoryMetadata = inventory.getMetadata();
            String description = inventoryMetadata == null || inventoryMetadata.description() == null
                    ? inventory.getDescription()
                    : inventoryMetadata.description();
            List<String> owners = inventoryMetadata == null || inventoryMetadata.owners() == null
                    ? inventory.getOwners() == null ? List.of() : List.of(inventory.getOwners())
                    : inventoryMetadata.owners();
            List<String> labels = inventoryMetadata == null || inventoryMetadata.labels() == null
                    ? List.of()
                    : inventoryMetadata.labels();
            List<String> teams = inventoryMetadata == null || inventoryMetadata.teams() == null
                    ? List.of()
                    : inventoryMetadata.teams();
            EnvironmentStatus environmentStatus = inventoryMetadata == null || inventoryMetadata.status() == null
                    ? EnvironmentStatus.FREE
                    : EnvironmentStatus.valueOf(inventoryMetadata.status());
            LocalDate expirationDate = inventoryMetadata == null || inventoryMetadata.expirationDate() == null
                    ? null
                    : LocalDate.parse(inventoryMetadata.expirationDate());
            EnvironmentType type = inventoryMetadata == null || inventoryMetadata.type() == null
                    ? EnvironmentType.ENVIRONMENT
                    : EnvironmentType.valueOf(inventoryMetadata.type());
            String role = inventoryMetadata == null
                    ? null
                    : inventoryMetadata.role();
            String region = inventoryMetadata == null
                    ? null
                    : inventoryMetadata.region();
            List<String> accessGroups = inventoryMetadata == null || inventoryMetadata.accessGroups() == null
                    ? List.of()
                    : inventoryMetadata.accessGroups();
            List<String> effectiveAccessGroups = inventoryMetadata == null || inventoryMetadata.effectiveAccessGroups() == null
                    ? List.of()
                    : inventoryMetadata.effectiveAccessGroups();
            return new CloudPassportEnvironment(inventory.getEnvironmentName(), description, namespaces,
                    owners, labels, teams, environmentStatus, expirationDate, type, role, region,
                    accessGroups, effectiveAccessGroups);
        } catch (IOException e) {
            throw new IllegalStateException("Error during read file: " + envDevinitionPath, e);
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
            throw new IllegalStateException("Error during read file: " + namespaceFilePath, e);
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
