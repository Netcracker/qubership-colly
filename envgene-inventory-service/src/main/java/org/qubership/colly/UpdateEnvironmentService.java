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
                                         Map<ParamsetContext, Map<String, Object>> parameters,
                                         CommitInfoDto commitInfo) {

        Map<String, Object> pipelineParameters = parameters.get(ParamsetContext.PIPELINE);
        if ((target.level() == ParamsetLevel.APPLICATION || target.level() == ParamsetLevel.NAMESPACE)
                && pipelineParameters != null && !pipelineParameters.isEmpty()) {
            throw new IllegalArgumentException(
                    "Pipeline parameters cannot be set for " + target.level() + " level. Environment=" + environment.getName()
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
            Map<String, Object> contextParams = parameters.get(context);
            if (contextParams == null) {
                continue;
            }

            Map<String, Object> effectiveParams = contextParams.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), Map::putAll);

            Set<String> nullKeys = contextParams.entrySet().stream()
                    .filter(e -> e.getValue() == null)
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toSet());

            // Part A: write or delete the ui-override file
            try {
                if (effectiveParams.isEmpty()) {
                    paramsetService.deleteParamsetFile(inventoryDir, target, applicationName, context);
                    paramsetService.removeParamsetReferenceFromEnvDefinition(inventoryDir, context, target, applicationName);
                } else {
                    paramsetService.writeParamsetFile(inventoryDir, target, applicationName, context, effectiveParams);
                    paramsetService.addParamsetReferenceToEnvDefinition(inventoryDir, context, target, applicationName);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Error updating paramset " + context + "/" + target.level() + " for " + target.deployPostfix(), e);
            }

            // Part B: remove null keys from every other source file that defines them
            if (!nullKeys.isEmpty()) {
                List<Paramset> sources = updatedParamsets.stream()
                        .filter(p -> p.paramsetContext() == context
                                && p.level() == target.level()
                                && Objects.equals(p.deployPostfix(), target.deployPostfix())
                                && Objects.equals(p.applicationName(), applicationName)
                                && p.sourceName() != null)
                        .toList();
                for (Paramset source : sources) {
                    Set<String> keysInSource = new HashSet<>(source.parameters().keySet());
                    keysInSource.retainAll(nullKeys);
                    if (!keysInSource.isEmpty()) {
                        try {
                            paramsetService.removeKeysFromParamsetFile(
                                    inventoryDir, source.sourceName(), applicationName,
                                    target.deployPostfix(), context, keysInSource);
                        } catch (IOException e) {
                            throw new IllegalStateException("Error removing keys from paramset " + source.sourceName(), e);
                        }
                    }
                }
            }

            // Part C: rebuild in-memory state
            // Collect non-ui-override Paramsets before removal so we can re-add them with keys stripped
            String uiOverrideName = paramsetService.calculateParamsetFileName(target.level(), target.deployPostfix(), applicationName, context);
            List<Paramset> otherSources = updatedParamsets.stream()
                    .filter(p -> p.paramsetContext() == context
                            && p.level() == target.level()
                            && Objects.equals(p.deployPostfix(), target.deployPostfix())
                            && Objects.equals(p.applicationName(), applicationName)
                            && !uiOverrideName.equals(p.sourceName()))
                    .toList();

            updatedParamsets.removeIf(p -> p.paramsetContext() == context
                    && p.level() == target.level()
                    && Objects.equals(p.deployPostfix(), target.deployPostfix())
                    && Objects.equals(p.applicationName(), applicationName));

            // When null keys were sent: re-add surviving other-source Paramsets BEFORE
            // ui-override so ui-override stays last (last-wins) in memory, matching disk order.
            if (!nullKeys.isEmpty()) {
                for (Paramset other : otherSources) {
                    Map<String, Object> surviving = new LinkedHashMap<>(other.parameters());
                    nullKeys.forEach(surviving::remove);
                    if (!surviving.isEmpty()) {
                        updatedParamsets.add(new Paramset(context, target.level(), target.deployPostfix(), applicationName, surviving, other.sourceName()));
                    }
                }
            }

            if (!effectiveParams.isEmpty()) {
                updatedParamsets.add(new Paramset(context, target.level(), target.deployPostfix(), applicationName, effectiveParams, uiOverrideName));
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
        yqService.updateYamlField(yamlPath, ".metadata.description", environmentUpdate.getDescription());
        Log.info("Updated metadata description to " + environmentUpdate.getDescription());
        yqService.deleteYamlField(yamlPath, ".inventory.owners");
        yqService.updateYamlArrayField(yamlPath, ".metadata.owners", environmentUpdate.getOwners());
        Log.info("Updated metadata owners to " + environmentUpdate.getOwners());
        yqService.updateYamlArrayField(yamlPath, ".metadata.labels", environmentUpdate.getLabels());
        Log.info("Updated metadata labels to " + environmentUpdate.getLabels());
        yqService.updateYamlArrayField(yamlPath, ".metadata.teams", environmentUpdate.getTeams());
        Log.info("Updated metadata teams to " + environmentUpdate.getTeams());
        yqService.updateYamlField(yamlPath, ".metadata.status", environmentUpdate.getStatus().name());
        Log.info("Updated status to " + environmentUpdate.getStatus().name());
        yqService.updateYamlField(yamlPath, ".metadata.expirationDate",
                environmentUpdate.getExpirationDate() == null ? null : environmentUpdate.getExpirationDate().toString());
        Log.info("Updated expirationDate to " + environmentUpdate.getExpirationDate());
        yqService.updateYamlField(yamlPath, ".metadata.type", environmentUpdate.getType().name());
        Log.info("Updated type to " + environmentUpdate.getType().name());
        yqService.updateYamlField(yamlPath, ".metadata.role", environmentUpdate.getRole());
        Log.info("Updated role to " + environmentUpdate.getRole());
    }
}
