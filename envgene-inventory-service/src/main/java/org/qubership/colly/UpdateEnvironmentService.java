package org.qubership.colly;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.qubership.colly.cloudpassport.GitInfo;
import org.qubership.colly.cloudpassport.envgen.EnvDefinition;
import org.qubership.colly.cloudpassport.envgen.Inventory;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
                    String content = Files.readString(path);
                    EnvDefinition yamlData = parseYaml(content);
                    updateYamlData(yamlData, environmentUpdate);
                    String updatedContent = writeYaml(yamlData);
                    Files.writeString(path, updatedContent);
                } catch (IOException e) {
                    throw new IllegalStateException("Error during update yaml for " + environmentUpdate.getName() + " cluster=" + cluster.getName(), e);
                }
            });

        } catch (IOException e) {
            Log.error("Error loading CloudPassports from " + gitRepoPathWithClusters, e);
        }

        gitService.commitAndPush(Paths.get(gitInfo.folderName()).toFile(), "Update environment " + environmentUpdate.getName());
        return environmentUpdate;
    }

    private EnvDefinition parseYaml(String yamlContent) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        System.out.println(yamlContent);
        return objectMapper.readValue(yamlContent, EnvDefinition.class);
    }

    private void updateYamlData(EnvDefinition envDefinition, Environment environmentUpdate) {
        Inventory inventory = envDefinition.getInventory();
        inventory.setDescription(environmentUpdate.getDescription());
        inventory.setOwner(environmentUpdate.getOwner());

//        if (environmentUpdate.getTeam() != null) {
//            inventory.put("team", environmentUpdate.getTeam());
//        }
//        if (environmentUpdate.getStatus() != null) {
//            inventory.put("status", environmentUpdate.getStatus().toString());
//        }
//        if (environmentUpdate.getType() != null) {
//            inventory.put("type", environmentUpdate.getType().toString());
//        }
//        if (environmentUpdate.getExpirationDate() != null) {
//            inventory.put("expirationDate", environmentUpdate.getExpirationDate().toString());
//        }
//        if (environmentUpdate.getLabels() != null && !environmentUpdate.getLabels().isEmpty()) {
//            inventory.put("labels", environmentUpdate.getLabels());
//        }
    }

    private String writeYaml(EnvDefinition yamlData) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);

        Yaml yaml = new Yaml(options);
        return yaml.dump(yamlData);
    }
}
