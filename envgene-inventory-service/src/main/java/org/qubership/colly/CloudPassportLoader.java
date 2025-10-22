package org.qubership.colly;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.qubership.colly.cloudpassport.CloudPassport;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;
import org.qubership.colly.cloudpassport.CloudPassportNamespace;
import org.qubership.colly.cloudpassport.GitInfo;
import org.qubership.colly.cloudpassport.envgen.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    @ConfigProperty(name = "colly.eis.env.instances.repo")
    Optional<List<String>> gitRepoUrls;


    public List<CloudPassport> loadCloudPassports() {
        List<GitInfo> gitInfos = cloneGitRepositories();

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

    private List<GitInfo> cloneGitRepositories() {
        if (gitRepoUrls.isEmpty()) {
            Log.error("gitRepoUrl parameter is not set. Skipping repository cloning.");
            return Collections.emptyList();
        }
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
        List<String> gitRepoUrlValues = gitRepoUrls.get();
        int index = 1;
        for (String gitRepoUrlValue : gitRepoUrlValues) {
            String folderNameToClone = cloudPassportFolder + "/" + index;
            gitService.cloneRepository(gitRepoUrlValue, new File(folderNameToClone));
            result.add(new GitInfo(gitRepoUrlValue, folderNameToClone));
            index++;
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
                    .filter(path -> path.getFileName().toString().equals(clusterName + ".yml"))
                    .map(this::parseCloudPassportDataFile)
                    .findFirst().orElseThrow();
        } catch (Exception e) {
            Log.error("Error loading Cloud Passport from " + cloudPassportFolderPath, e);
            return null;
        }

        String token;
        try (Stream<Path> credsPath = Files.list(cloudPassportFolderPath)) {
            token = credsPath
                    .filter(path -> path.getFileName().toString().equals(clusterName + "-creds.yml"))
                    .map(path -> parseTokenFromCredsFile(path, cloudPassportData))
                    .findFirst().orElseThrow();

        } catch (Exception e) {
            Log.error("Error loading Cloud Passport from " + cloudPassportFolderPath, e);
            return null;
        }
        CloudData cloud = cloudPassportData.getCloud();
        String cloudApiHost = cloud.getCloudProtocol() + "://" + cloud.getCloudApiHost() + ":" + cloud.getCloudApiPort();
        Log.info("Cloud API Host: " + cloudApiHost);
        CSEData cse = cloudPassportData.getCse();
        URI monitoringUri = null;
        if (cse != null) {
            if (cse.getMonitoringExtMonitoringQueryUrl() != null && !cse.getMonitoringExtMonitoringQueryUrl().isEmpty()) {
                monitoringUri = URI.create(cse.getMonitoringExtMonitoringQueryUrl());
            } else if (cse.getMonitoringNamespace() != null && MONITORING_TYPE_VICTORIA_DB.equals(cse.getMonitoringType())) {
                monitoringUri = URI.create("http://vmsingle-k8s." + cse.getMonitoringNamespace() + ":8429");
            }
        }
        Log.info("Monitoring URI: " + monitoringUri);
        return new CloudPassport(clusterName, token, cloudApiHost, environments, monitoringUri, gitInfo);
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
            String description = inventoryMetadata == null || inventoryMetadata.getDescription() == null
                    ? inventory.getDescription()
                    : inventoryMetadata.getDescription();
            String owners = inventoryMetadata == null || inventoryMetadata.getOwners() == null
                    ? inventory.getOwners()
                    : inventoryMetadata.getOwners();
            return new CloudPassportEnvironment(inventory.getEnvironmentName(), description, owners, namespaces);
        } catch (IOException e) {
            throw new IllegalStateException("Error during read file: " + envDevinitionPath, e);
        }
    }

    private CloudPassportNamespace parseNamespaceFile(Path namespaceFilePath) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (FileInputStream inputStream = new FileInputStream(namespaceFilePath.toFile())) {
            Namespace namespace = mapper.readValue(inputStream, Namespace.class);
            Log.info("Processing namespace " + namespace.getName());
            return new CloudPassportNamespace(namespace.getName());
        } catch (IOException e) {
            throw new IllegalStateException("Error during read file: " + namespaceFilePath, e);
        }
    }

    String parseTokenFromCredsFile(Path path, CloudPassportData cloudPassportData) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (FileInputStream inputStream = new FileInputStream(path.toFile())) {
            JsonNode jsonNode = mapper.readTree(inputStream);
            JsonNode tokenNode = jsonNode.get(cloudPassportData.getCloud().getCloudDeployToken());
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
            if (data != null && data.getCloud() != null) {
                return data;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error during read file: " + filePath, e);
        }
        throw new IllegalArgumentException("Can't read cloud passport data from " + filePath);
    }
}
