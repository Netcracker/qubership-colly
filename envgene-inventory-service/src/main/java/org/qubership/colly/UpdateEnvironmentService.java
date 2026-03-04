package org.qubership.colly;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.qubership.colly.cloudpassport.GitInfo;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.data.ParamsetContext;
import org.qubership.colly.db.data.ParamsetLevel;
import org.qubership.colly.dto.CommitInfoDto;
import org.qubership.colly.dto.ParameterDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
public class UpdateEnvironmentService {

    @Inject
    GitService gitService;

    @Inject
    ParamsetService paramsetService;

    @Inject
    YqService yqService;

    public void updateParamset(Cluster cluster, Environment environment, ParamsetLevel level,
                               String deployPostfix, String applicationName,
                               Map<ParamsetContext, List<ParameterDto>> parameters,
                               CommitInfoDto commitInfo) {
        GitInfo gitInfo = cluster.getGitInfo();
        Path gitRepoPath = Paths.get(gitInfo.folderName());
        if (!Files.exists(gitRepoPath)) {
            throw new IllegalArgumentException("Could not find git repo at " + gitRepoPath);
        }

        Path inventoryDir = findInventoryDir(gitRepoPath, environment.getName());

        for (ParamsetContext context : ParamsetContext.values()) {
            List<ParameterDto> parameterDtos = parameters.get(context);
            if (parameterDtos == null) {
                continue;
            }

            try {
                if (parameterDtos.isEmpty()) {
                    paramsetService.deleteParamsetFile(inventoryDir, level, deployPostfix, applicationName, context);
                    paramsetService.removeParamsetReferenceFromEnvDefinition(inventoryDir, context, deployPostfix, level, applicationName);
                } else {
                    paramsetService.writeParamsetFile(inventoryDir, level, deployPostfix, applicationName, context, parameterDtos);
                    paramsetService.addParamsetReferenceToEnvDefinition(inventoryDir, context, deployPostfix, level, applicationName);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Error updating paramset " + context + "/" + level + " for " + deployPostfix, e);
            }
        }

        String commitMessage = commitInfo.commitMessage() != null
                ? commitInfo.commitMessage()
                : "Update UI parameters for " + environment.getName();
        gitService.commitAndPush(gitRepoPath.toFile(), commitMessage, null, commitInfo.username(), commitInfo.email());
    }

    private Path findInventoryDir(Path gitRepoPath, String environmentName) {
        try (Stream<Path> paths = Files.walk(gitRepoPath)) {
            return paths.filter(Files::isDirectory)
                    .map(path -> path.resolve(environmentName))
                    .filter(Files::isDirectory)
                    .map(path -> path.resolve("Inventory"))
                    .filter(Files::isDirectory)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Could not find Inventory directory for environment " + environmentName));
        } catch (IOException e) {
            throw new IllegalStateException("Error searching for Inventory directory for environment " + environmentName, e);
        }
    }

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
                    updateEnvDefinitionYamlFileWithYq(path, environmentUpdate);
                    Log.info("Updated yaml for " + environmentUpdate.getName() + " cluster=" + cluster.getName());
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

    private void updateEnvDefinitionYamlFileWithYq(Path yamlPath, Environment environmentUpdate) throws IOException {
        if (!yqService.isYqAvailable()) {
            throw new IllegalStateException("yq is not available. Please install yq to use this feature.");
        }
        Log.info("Updating yaml file " + yamlPath);
        yqService.deleteYamlField(yamlPath, ".inventory.description");
        yqService.updateYamlField(yamlPath, ".inventory.metadata.description", environmentUpdate.getDescription());
        Log.info("Updated metadata description to " + environmentUpdate.getDescription());
        yqService.deleteYamlField(yamlPath, ".inventory.owners");
        yqService.updateYamlArrayField(yamlPath, ".inventory.metadata.owners", environmentUpdate.getOwners());
        Log.info("Updated metadata owners to " + environmentUpdate.getOwners());
        yqService.updateYamlArrayField(yamlPath, ".inventory.metadata.labels", environmentUpdate.getLabels());
        Log.info("Updated metadata labels to " + environmentUpdate.getLabels());
        yqService.updateYamlArrayField(yamlPath, ".inventory.metadata.teams", environmentUpdate.getTeams());
        Log.info("Updated metadata teams to " + environmentUpdate.getTeams());
        yqService.updateYamlField(yamlPath, ".inventory.metadata.status", environmentUpdate.getStatus().name());
        Log.info("Updated status to " + environmentUpdate.getStatus().name());
        yqService.updateYamlField(yamlPath, ".inventory.metadata.expirationDate",
                environmentUpdate.getExpirationDate() == null ? null : environmentUpdate.getExpirationDate().toString());
        Log.info("Updated expirationDate to " + environmentUpdate.getExpirationDate());
        yqService.updateYamlField(yamlPath, ".inventory.metadata.type", environmentUpdate.getType().name());
        Log.info("Updated type to " + environmentUpdate.getType().name());
        yqService.updateYamlField(yamlPath, ".inventory.metadata.role", environmentUpdate.getRole());
        Log.info("Updated role to " + environmentUpdate.getRole());
    }
}
