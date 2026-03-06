package org.qubership.colly;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.qubership.colly.cloudpassport.GitInfo;
import org.qubership.colly.cloudpassport.Paramset;
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
import java.util.*;
import java.util.stream.Stream;

@ApplicationScoped
public class UpdateEnvironmentService {

    @Inject
    GitService gitService;

    @Inject
    ParamsetService paramsetService;

    @Inject
    YqService yqService;

    public List<Paramset> updateParamset(Cluster cluster, Environment environment, ParamsetService.ParamsetTarget target,
                                         String applicationName,
                                         Map<ParamsetContext, List<ParameterDto>> parameters,
                                         CommitInfoDto commitInfo) {

        List<ParameterDto> pipelineParameters = parameters.get(ParamsetContext.PIPELINE);
        if (target.level() == ParamsetLevel.APPLICATION
                && pipelineParameters != null && !pipelineParameters.isEmpty()) {
            throw new IllegalArgumentException(
                    "Pipeline parameters cannot be set for Application level. Environment=" + environment.getName()
                            + ", application=" + applicationName);
        }

        GitInfo gitInfo = cluster.getGitInfo();
        Path gitRepoPath = Paths.get(gitInfo.folderName());
        if (!Files.exists(gitRepoPath)) {
            throw new IllegalArgumentException("Could not find git repo at " + gitRepoPath);
        }
        Path inventoryDir = findInventoryDir(gitRepoPath, environment.getName());

        List<Paramset> updatedParamsets = new ArrayList<>(environment.getParamsets());

        for (ParamsetContext context : ParamsetContext.values()) {
            List<ParameterDto> parameterDtos = parameters.get(context);
            if (parameterDtos == null) {
                continue;
            }

            try {
                if (parameterDtos.isEmpty()) {
                    paramsetService.deleteParamsetFile(inventoryDir, target, applicationName, context);
                    paramsetService.removeParamsetReferenceFromEnvDefinition(inventoryDir, context, target, applicationName);
                } else {
                    paramsetService.writeParamsetFile(inventoryDir, target, applicationName, context, parameterDtos);
                    paramsetService.addParamsetReferenceToEnvDefinition(inventoryDir, context, target, applicationName);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Error updating paramset " + context + "/" + target.level() + " for " + target.deployPostfix(), e);
            }
            updatedParamsets.removeIf(p -> p.paramsetContext() == context
                    && p.level() == target.level()
                    && Objects.equals(p.deployPostfix(), target.deployPostfix())
                    && Objects.equals(p.applicationName(), applicationName));
            if (!parameterDtos.isEmpty()) {
                Map<String, String> newParams = new LinkedHashMap<>();
                parameterDtos.forEach(p -> newParams.put(p.name(), p.value()));
                updatedParamsets.add(new Paramset(context, target.level(), target.deployPostfix(), applicationName, newParams));
            }
        }

        String commitMessage = commitInfo.commitMessage() != null
                ? commitInfo.commitMessage()
                : "Update UI parameters for " + environment.getName();
        gitService.commitAndPush(gitRepoPath.toFile(), commitMessage, null, commitInfo.username(), commitInfo.email());

        return updatedParamsets;
    }

    public Environment updateEnvironment(Cluster cluster, Environment environmentUpdate) {
        GitInfo gitInfo = cluster.getGitInfo();
        Path gitRepoPath = Paths.get(gitInfo.folderName());
        if (!Files.exists(gitRepoPath)) {
            throw new IllegalArgumentException("Could not find git repo at " + gitRepoPath);
        }
        Path inventoryDir = findInventoryDir(gitRepoPath, environmentUpdate.getName());
        Path envDefinitionPath = inventoryDir.resolve("env_definition.yml");
        try {
            updateEnvDefinitionYamlFileWithYq(envDefinitionPath, environmentUpdate);
            Log.info("Updated yaml for " + environmentUpdate.getName() + " cluster=" + cluster.getName());
        } catch (IOException e) {
            throw new IllegalStateException("Error during update yaml for " + environmentUpdate.getName() + " cluster=" + cluster.getName(), e);
        }
        gitService.commitAndPush(gitRepoPath.toFile(), "Update environment " + environmentUpdate.getName());

        return environmentUpdate;
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
