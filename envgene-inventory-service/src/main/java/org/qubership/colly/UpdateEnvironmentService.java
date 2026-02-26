package org.qubership.colly;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.qubership.colly.cloudpassport.GitInfo;
import org.qubership.colly.cloudpassport.envgen.ParamsetFileData;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.data.ParamsetContext;
import org.qubership.colly.db.data.ParamsetLevel;
import org.qubership.colly.dto.CommitInfoDto;
import org.qubership.colly.dto.ParameterDto;
import org.qubership.colly.dto.UiParametersDto;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class UpdateEnvironmentService {
    @Inject
    GitService gitService;


    public void updateParamset(Cluster cluster, Environment environment, ParamsetLevel level,
                               String deployPostfix, String applicationName,
                               UiParametersDto parameters, CommitInfoDto commitInfo) {
        GitInfo gitInfo = cluster.getGitInfo();
        Path gitRepoPath = Paths.get(gitInfo.folderName());
        if (!Files.exists(gitRepoPath)) {
            throw new IllegalArgumentException("Could not find git repo at " + gitRepoPath);
        }

        Path inventoryDir = findInventoryDir(gitRepoPath, environment.getName());

        for (ParamsetContext context : ParamsetContext.values()) {
            List<ParameterDto> parameterDtos = parameters.parameters().get(context);
            if (parameterDtos == null || parameterDtos.isEmpty()) {
                continue;
            }
            Map<String, String> newParams = new LinkedHashMap<>();
            parameterDtos.forEach(p -> newParams.put(p.name(), p.value()));

            String fileSuffix = calculateFileSuffix(context);
            String fileName = calculateParamsetFileName(level, deployPostfix, applicationName, fileSuffix);
            Path paramsetFilePath = inventoryDir.resolve("parameters").resolve(fileName);

            try {
                writeParamsetFile(paramsetFilePath, level, applicationName, newParams);
                Log.info("Written paramset file: " + paramsetFilePath);
            } catch (IOException e) {
                throw new IllegalStateException("Error writing paramset file " + paramsetFilePath, e);
            }
        }
        //todo update env_definition file

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

    private String calculateFileSuffix(ParamsetContext context) {
        return switch (context) {
            case DEPLOYMENT -> "deploy-ui-override";
            case RUNTIME -> "runtime-ui-override";
            case PIPELINE -> "pipeline-ui-override";
        };
    }

    private String calculateParamsetFileName(ParamsetLevel level, String deployPostfix, String applicationName, String suffix) {
        return switch (level) {
            case ENVIRONMENT -> suffix + ".yaml";
            case NAMESPACE -> deployPostfix + "-" + suffix + ".yaml";
            case APPLICATION -> deployPostfix + "-" + applicationName + "-" + suffix + ".yaml";
        };
    }

    private void writeParamsetFile(Path filePath, ParamsetLevel level, String applicationName,
                                   Map<String, String> parameters) throws IOException {
        Files.createDirectories(filePath.getParent());
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        ParamsetFileData fileData;
        if (level == ParamsetLevel.APPLICATION) {
            List<ParamsetFileData.ParamsetApplicationData> apps = new ArrayList<>();
            if (Files.isRegularFile(filePath)) {
                ParamsetFileData existing = mapper.readValue(filePath.toFile(), ParamsetFileData.class);
                if (existing.applications() != null) {
                    existing.applications().stream()
                            .filter(a -> !applicationName.equals(a.appName()))
                            .forEach(apps::add);
                }
            }
            apps.add(new ParamsetFileData.ParamsetApplicationData(applicationName, parameters));
            fileData = new ParamsetFileData(filePath.getFileName().toString(), null, apps);
        } else {
            fileData = new ParamsetFileData(filePath.getFileName().toString(), parameters, null);
        }

        mapper.writeValue(filePath.toFile(), fileData);
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
                    updateYamlFileWithYq(path, environmentUpdate);
                    Log.info("Updated yaml for " + environmentUpdate.getName() + " cluster=" + cluster.getName());
                } catch (IOException | InterruptedException e) {
                    throw new IllegalStateException("Error during update yaml for " + environmentUpdate.getName() + " cluster=" + cluster.getName(), e);
                }
            });

        } catch (IOException e) {
            Log.error("Error loading CloudPassports from " + gitRepoPathWithClusters, e);
        }
        gitService.commitAndPush(Paths.get(gitInfo.folderName()).toFile(), "Update environment " + environmentUpdate.getName());

        return environmentUpdate;
    }

    private void updateYamlFileWithYq(Path yamlPath, Environment environmentUpdate) throws IOException, InterruptedException {
        if (!isYqAvailable()) {
            throw new IllegalStateException("yq is not available. Please install yq to use this feature.");
        }
        Log.info("Updating yaml file " + yamlPath);
        deleteYamlField(yamlPath, ".inventory.description");
        updateYamlField(yamlPath, ".inventory.metadata.description", environmentUpdate.getDescription());
        Log.info("Updated metadata description to " + environmentUpdate.getDescription());
        deleteYamlField(yamlPath, ".inventory.owners");
        updateYamlArrayField(yamlPath, ".inventory.metadata.owners", environmentUpdate.getOwners());
        Log.info("Updated metadata owners to " + environmentUpdate.getOwners());
        updateYamlArrayField(yamlPath, ".inventory.metadata.labels", environmentUpdate.getLabels());
        Log.info("Updated metadata labels to " + environmentUpdate.getLabels());
        updateYamlArrayField(yamlPath, ".inventory.metadata.teams", environmentUpdate.getTeams());
        Log.info("Updated metadata teams to " + environmentUpdate.getTeams());
        updateYamlField(yamlPath, ".inventory.metadata.status", environmentUpdate.getStatus().name());
        Log.info("Updated status to " + environmentUpdate.getStatus().name());
        updateYamlField(yamlPath, ".inventory.metadata.expirationDate", environmentUpdate.getExpirationDate() == null ? null : environmentUpdate.getExpirationDate().toString());
        Log.info("Updated expirationDate to " + environmentUpdate.getExpirationDate());
        updateYamlField(yamlPath, ".inventory.metadata.type", environmentUpdate.getType().name());
        Log.info("Updated type to " + environmentUpdate.getType().name());
        updateYamlField(yamlPath, ".inventory.metadata.role", environmentUpdate.getRole());
        Log.info("Updated role to " + environmentUpdate.getRole());
    }

    private boolean isYqAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("yq", "--version");
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            Log.error("Error checking yq availability:", e);
            return false;
        }
    }

    private void deleteYamlField(Path yamlPath, String yamlFieldPath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "yq",
                "eval",
                "del(" + yamlFieldPath + ")",
                yamlPath.toString(),
                "--inplace"
        );
        executeYqCommand(pb);
    }

    private void updateYamlField(Path yamlPath, String yamlFieldPath, String value) throws IOException, InterruptedException {
        if (value == null || value.isEmpty()) {
            deleteYamlField(yamlPath, yamlFieldPath);
            return;
        }
        ProcessBuilder pb = new ProcessBuilder(
                "yq",
                "eval",
                yamlFieldPath + " = " + "\"" + escapeForYq(value) + "\"",
                yamlPath.toString(),
                "--inplace"
        );
        executeYqCommand(pb);
    }

    private void updateYamlArrayField(Path yamlPath, String yamlFieldPath, List<String> values) throws IOException, InterruptedException {
        if (values == null || values.isEmpty()) {
            deleteYamlField(yamlPath, yamlFieldPath);
            return;
        }
        String arrayValue = values.stream()
                .map(value -> "\"" + escapeForYq(value) + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
        ProcessBuilder pb = new ProcessBuilder(
                "yq",
                "eval",
                yamlFieldPath + " = " + arrayValue,
                yamlPath.toString(),
                "--inplace"
        );
        executeYqCommand(pb);
    }

    private void executeYqCommand(ProcessBuilder processBuilder) throws IOException, InterruptedException {
        Process process = processBuilder.start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            throw new IOException("yq command timed out");
        }

        if (process.exitValue() != 0) {
            StringBuilder output = new StringBuilder();
            try (BufferedReader bufferedReader = process.errorReader()) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                throw new IOException("yq command failed with exit code " + process.exitValue() + ": " + output.toString().trim());
            }
        }
    }

    private String escapeForYq(String value) {
        return value.replace("\"", "\\\"")
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
