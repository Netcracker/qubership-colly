package org.qubership.colly;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.qubership.colly.data.CloudData;
import org.qubership.colly.data.CloudPassport;
import org.qubership.colly.data.CloudPassportData;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

@ApplicationScoped
public class CloudPassportLoader {

    private static final String CLOUD_PASSPORT_FOLDER = "cloud-passport";
    @ConfigProperty(name = "env.instances.path")
    String envInstancesPath;


    public List<CloudPassport> loadCloudPassports() {
        try (Stream<Path> paths = Files.list(Paths.get(envInstancesPath))) {
            return paths.filter(Files::isDirectory)
                    .map(path -> path.resolve(CLOUD_PASSPORT_FOLDER))
                    .filter(Files::isDirectory)
                    .map(path -> processYamlFilesInCloudPassportFolder(path, path.getParent().getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private CloudPassport processYamlFilesInCloudPassportFolder(Path folderPath, String rootFolderName) {
        Log.info("Loading Cloud Passport from " + folderPath);
        CloudPassportData cloudPassportData;
        try (Stream<Path> paths = Files.list(folderPath)) {
            cloudPassportData = paths
                    .filter(path -> path.getFileName().toString().equals(rootFolderName + ".yml"))
                    .map(this::parseCloudPassportDataFile)
                    .findFirst().orElseThrow();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String token;
        try (Stream<Path> credsPath = Files.list(folderPath)) {
            token = credsPath
                    .filter(path -> path.getFileName().toString().equals(rootFolderName + "-creds.yml"))
                    .map(path -> parseTokenFromCredsFile(path, cloudPassportData))
                    .findFirst().orElseThrow();

        } catch (IOException e) {
            throw new RuntimeException(e);
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
