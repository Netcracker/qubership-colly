package org.qubership.colly;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.qubership.colly.data.CloudData;
import org.qubership.colly.data.CloudPassport;
import org.qubership.colly.data.CloudPassportData;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
public class CloudPassportLoader {

    private static final String CLOUD_PASSPORT_FOLDER = "cloud-passport";
    private static final String GIT_DIRECTORY = "./git-repo";

    @ConfigProperty(name = "env.instances.repo")
    Optional<String> gitRepoUrl;


    private void cloneGitRepository() {
        if (gitRepoUrl.isEmpty()) {
            Log.error("gitRepoUrl parameter is not set. Skipping repository cloning.");
            return;
        }
        File directory = new File(GIT_DIRECTORY);
        if (directory.exists()) {
            Log.info("Repository was already cloned. Directory: " + directory);
            return;
        }

        String gitRepoUrlValue = gitRepoUrl.get();
        try {
            Log.info("Cloning repository: " + gitRepoUrlValue);
            Git.cloneRepository()
                    .setURI(gitRepoUrlValue)
                    .setDirectory(directory)
                    .call();
            Log.info("Repository cloned.");
        } catch (GitAPIException e) {
            throw new RuntimeException("Error during clone repository: " + gitRepoUrlValue, e);
        }
    }

    public List<CloudPassport> loadCloudPassports() {
        cloneGitRepository();
        Path dir = Paths.get(GIT_DIRECTORY);
        if (!dir.toFile().exists()) {
            return Collections.emptyList();
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.filter(Files::isDirectory)
                    .map(path -> path.resolve(CLOUD_PASSPORT_FOLDER))
                    .filter(Files::isDirectory)
                    .map(path -> processYamlFilesInCloudPassportFolder(path, path.getParent().getFileName().toString()))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            Log.error("Error loading CloudPassports from " + dir, e);
        }
        return Collections.emptyList();
    }


    private CloudPassport processYamlFilesInCloudPassportFolder(Path folderPath, String rootFolderName) {
        Log.info("Loading Cloud Passport from " + folderPath);
        CloudPassportData cloudPassportData;
        try (Stream<Path> paths = Files.list(folderPath)) {
            cloudPassportData = paths
                    .filter(path -> path.getFileName().toString().equals(rootFolderName + ".yml"))
                    .map(this::parseCloudPassportDataFile)
                    .findFirst().orElseThrow();
        } catch (Exception e) {
            Log.error("Error loading Cloud Passport from " + folderPath, e);
            return null;
        }

        String token;
        try (Stream<Path> credsPath = Files.list(folderPath)) {
            token = credsPath
                    .filter(path -> path.getFileName().toString().equals(rootFolderName + "-creds.yml"))
                    .map(path -> parseTokenFromCredsFile(path, cloudPassportData))
                    .findFirst().orElseThrow();

        } catch (Exception e) {
            Log.error("Error loading Cloud Passport from " + folderPath, e);
            return null;
        }
        CloudData cloud = cloudPassportData.getCloud();
        String cloudApiHost = cloud.getCloudProtocol() + "://" + cloud.getCloudApiHost() + ":" + cloud.getCloudApiPort();
        return new CloudPassport(rootFolderName, token, cloudApiHost);
    }

    private String parseTokenFromCredsFile(Path path, CloudPassportData cloudPassportData) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (FileInputStream inputStream = new FileInputStream(path.toFile())) {
            JsonNode jsonNode = mapper.readTree(inputStream);
            JsonNode tokenNode = jsonNode.get(cloudPassportData.getCloud().getCloudDeployToken());
            if (tokenNode != null) {
                return tokenNode.findValue("secret").asText();
            }

        } catch (IOException e) {
            throw new RuntimeException("Error during read file: " + path, e);
        }
        throw new RuntimeException("Can't read cloud passport data creds from " + path);
    }

    private CloudPassportData parseCloudPassportDataFile(Path filePath) {

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (FileInputStream inputStream = new FileInputStream(filePath.toFile())) {
            CloudPassportData data = mapper.readValue(inputStream, CloudPassportData.class);
            if (data != null && data.getCloud() != null) {
                return data;
            }
        } catch (IOException e) {
            throw new RuntimeException("Error during read file: " + filePath, e);
        }
        throw new RuntimeException("Can't read cloud passport data from " + filePath);
    }
}
