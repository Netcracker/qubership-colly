package org.qubership.colly;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.qubership.colly.cloudpassport.GitInfo;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
public class UpdateEnvironmentService {
    @Inject
    GitService gitService;


    public Environment updateEnvironment(Cluster cluster, Environment environmentUpdate) {
        GitInfo gitInfo = cluster.getGitInfo();
        Path gitRepoPathWithClusters = Paths.get(gitInfo.folderName());
        if (!Files.exists(gitRepoPathWithClusters)) {
            throw new IllegalArgumentException("Could not find git repo at " + gitRepoPathWithClusters);
        }
        try (Stream<Path> paths = Files.walk(gitRepoPathWithClusters)) {
            Optional<Path> envDefinitionPath = paths.filter(Files::isDirectory)
                    .map(path -> path.resolve(environmentUpdate.getName()))
                    .filter(Files::isDirectory)
                    .map(path -> path.resolve("Inventory"))
                    .map(path -> path.resolve("env_definition.yml"))
                    .findFirst();
            if (envDefinitionPath.isEmpty()) {
                throw new IllegalArgumentException("Could not find env_definition.yml for " + environmentUpdate.getName() + " in cluster " + cluster.getName());
            }
            envDefinitionPath.ifPresent(path -> {
                try {
                    System.out.println("roro: " + path);
                    String content = Files.readString(path);
                    System.out.println("Content before update:\n" + content);
                    Map<String, Object> yamlData = parseYaml(content);
                    updateYamlData(yamlData, environmentUpdate);
                    String updatedContent = writeYaml(yamlData);
                    System.out.println("Content after update:\n" + updatedContent);
                    Files.writeString(path, updatedContent);
                } catch (IOException e) {
                    throw new IllegalStateException("Error during update yaml for " + environmentUpdate.getName() + " cluster=" + cluster.getName(), e);
                }
            });

        } catch (IOException e) {
            Log.error("Error loading CloudPassports from " + gitRepoPathWithClusters, e);
        }

        gitService.commitAndPush(Paths.get(gitInfo.folderName()).toFile(), "Update environment " + environmentUpdate.getName(), "todo");
        return environmentUpdate;
    }

    private Map<String, Object> parseYaml(String yamlContent) {
        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(yamlContent);
        return data != null ? data : new LinkedHashMap<>();
    }

    private void updateYamlData(Map<String, Object> yamlData, Environment environmentUpdate) {
        Map<String, Object> inventory = (Map<String, Object>) yamlData.computeIfAbsent("inventory", k -> new LinkedHashMap<>());

        if (environmentUpdate.getDescription() != null) {
            inventory.put("description", environmentUpdate.getDescription());
        }
        if (environmentUpdate.getOwner() != null) {
            inventory.put("owners", environmentUpdate.getOwner());
        }
        if (environmentUpdate.getTeam() != null) {
            inventory.put("team", environmentUpdate.getTeam());
        }
        if (environmentUpdate.getStatus() != null) {
            inventory.put("status", environmentUpdate.getStatus().toString());
        }
        if (environmentUpdate.getType() != null) {
            inventory.put("type", environmentUpdate.getType().toString());
        }
        if (environmentUpdate.getExpirationDate() != null) {
            inventory.put("expirationDate", environmentUpdate.getExpirationDate().toString());
        }
        if (environmentUpdate.getLabels() != null && !environmentUpdate.getLabels().isEmpty()) {
            inventory.put("labels", environmentUpdate.getLabels());
        }
    }

    private String writeYaml(Map<String, Object> yamlData) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);

        Yaml yaml = new Yaml(options);
        return yaml.dump(yamlData);
    }
}
