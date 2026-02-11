package org.qubership.colly.projectrepo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.qubership.colly.GitService;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;


@ApplicationScoped
public class ProjectRepoLoader {
    @Inject
    GitService gitService;

    @ConfigProperty(name = "colly.eis.project.repo.folder")
    String projectRepoFolder;

    @ConfigProperty(name = "colly.eis.project.repo.url")
    String projectGitRepoUrl;


    public List<Project> loadProjects() {
        File directory = new File(projectRepoFolder);

        try {
            if (directory.exists()) {
                FileUtils.deleteDirectory(directory);
            }
        } catch (IOException e) {
            Log.error("Impossible to start git cloning. Failed to clean directory: " + projectRepoFolder, e);
            return null;
        }

        gitService.cloneRepository(projectGitRepoUrl, null, null, directory);

        Path dir = Paths.get(projectRepoFolder);
        ClusterDefaults clusterDefaults = getClusterDefaults(dir);

        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(path -> path.toString().endsWith("parameters.yaml") || path.endsWith("parameters.yml"))
                    .map(path -> processProject(path, path.getParent(), clusterDefaults)).filter(Objects::nonNull).toList();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ClusterDefaults getClusterDefaults(Path dir) {
        ClusterDefaults clusterDefaults;
        try (Stream<Path> walk = Files.walk(dir)) {
            clusterDefaults = walk.filter(path -> path.toString().endsWith("defaults.yaml") || path.endsWith("defaults.yml"))
                    .map(path -> processDefaultsFile(path, path.getParent())).filter(Objects::nonNull).findFirst().orElse(null);

        } catch (IOException e) {
            Log.error("Error loading cluster defaults:", e);
            return null;
        }
        return clusterDefaults;
    }

    private ClusterDefaults processDefaultsFile(Path pathToDefaultsFile, Path parent) {
        if (!parent.getFileName().toString().equals("defaults")) {
            Log.error("Defaults file found in unexpected location: " + pathToDefaultsFile);
            return null;
        }
        Log.info("Processing cluster defaults from file: " + pathToDefaultsFile);
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (FileInputStream inputStream = new FileInputStream(pathToDefaultsFile.toFile())) {
            JsonNode root = mapper.readTree(inputStream);
            JsonNode clustersNode = root.get("clusters");
            if (clustersNode == null) {
                Log.warn("No 'clusters' section found in defaults file: " + pathToDefaultsFile);
                return null;
            }
            return mapper.treeToValue(clustersNode, ClusterDefaults.class);
        } catch (Exception e) {
            Log.error("Can't read project data from file: " + pathToDefaultsFile, e);
            return null;
        }
    }

    Project processProject(Path parametersFilePath, Path projectPath, ClusterDefaults clusterDefaults) {
        String projectId = projectPath.getFileName().toString();
        Log.info("processing project: " + projectId + " from file: " + parametersFilePath.toString());
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (FileInputStream inputStream = new FileInputStream(parametersFilePath.toFile())) {
            ProjectEntity projectEntity = mapper.readValue(inputStream, ProjectEntity.class);

            List<RepositoryEntity> envgeneInstanceRepos = projectEntity.repositories.stream()
                    .filter(repositoryEntity -> "envgeneInstance".equals(repositoryEntity.type()))
                    .toList();

            List<RepositoryEntity> envgeneTemplateRepos = projectEntity.repositories.stream()
                    .filter(repositoryEntity -> "envgeneTemplate".equals(repositoryEntity.type()))
                    .toList();

            List<RepositoryEntity> pipelineRepos = projectEntity.repositories.stream()
                    .filter(repositoryEntity -> {
                        String type = repositoryEntity.type();
                        return "clusterProvision".equals(type)
                                || "envProvision".equals(type)
                                || "solutionDeploy".equals(type);
                    })
                    .toList();

            return new Project(projectId,
                    projectEntity.name(),
                    ProjectType.fromString(projectEntity.type),
                    projectEntity.customerName(),
                    convertToInstanceRepositories(envgeneInstanceRepos),
                    convertToPipelines(pipelineRepos),
                    ClusterPlatform.fromString(projectEntity.clusterPlatform()),
                    convertToEnvgeneTemplateRepository(envgeneTemplateRepos, projectId),
                    projectEntity.accessGroups() == null ? List.of() : projectEntity.accessGroups(),
                    clusterDefaults,
                    projectEntity.mavenRepoName());
        } catch (Exception e) {
            Log.error("Can't read project data from file: " + parametersFilePath, e);
            return null;
        }
    }

    private List<InstanceRepository> convertToInstanceRepositories(List<RepositoryEntity> envgeneInstanceRepos) {
        return envgeneInstanceRepos.stream()
                .map(repoEntity -> new InstanceRepository(
                        repoEntity.url(),
                        repoEntity.branch(),
                        repoEntity.token(),
                        repoEntity.region()))
                .toList();
    }

    private EnvgeneTemplateRepository convertToEnvgeneTemplateRepository(List<RepositoryEntity> repositoryEntities, String projectId) {
        if (repositoryEntities.size() > 1) {
            Log.warn("More than one envgeneTemplate repository found for project: " + projectId);
        }
        if (repositoryEntities.isEmpty()) {
            Log.warn("No envgeneTemplate repository found for project: " + projectId);
            return null;
        }
        return repositoryEntities.stream()
                .findFirst()
                .map(repoEntity -> new EnvgeneTemplateRepository(
                        repoEntity.url(),
                        repoEntity.token(),
                        repoEntity.branch(),
                        repoEntity.envgeneArtifact()))
                .orElse(null);
    }

    private List<Pipeline> convertToPipelines(List<RepositoryEntity> pipelineRepos) {
        return pipelineRepos.stream()
                .map(repoEntity -> new Pipeline(
                        PipelineType.fromString(repoEntity.type()),
                        repoEntity.url(),
                        repoEntity.branch(),
                        repoEntity.token(),
                        repoEntity.region()))
                .filter(pipeline -> pipeline.type() != null) // фильтруем невалидные типы
                .toList();
    }

    public record ProjectEntity(String name, String customerName, String type,
                                List<RepositoryEntity> repositories, String clusterPlatform,
                                List<String> accessGroups, String mavenRepoName) {
    }

    public record RepositoryEntity(String type, String url, String token, String region, String branch,
                                   EnvgeneArtifact envgeneArtifact) {
    }


}
