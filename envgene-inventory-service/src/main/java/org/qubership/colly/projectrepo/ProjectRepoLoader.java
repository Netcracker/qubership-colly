package org.qubership.colly.projectrepo;

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
import java.util.UUID;

@ApplicationScoped
public class ProjectRepoLoader {
    @Inject
    GitService gitService;

    @ConfigProperty(name = "colly.eis.project.repo.folder")
    String projectRepoFolder;

    @ConfigProperty(name = "colly.eis.project.repo.url")
    String projectGitRepoUrl;


    public List<Project> loadProjects() {
        if (projectGitRepoUrl.isEmpty()) {
            Log.error("colly.eis.env.project.repo parameter is not set. Skipping repository cloning.");
            return null;
        }
        File directory = new File(projectRepoFolder);

        try {
            if (directory.exists()) {
                FileUtils.deleteDirectory(directory);
            }
        } catch (IOException e) {
            Log.error("Impossible to start git cloning. Failed to clean directory: " + projectRepoFolder, e);
            return null;
        }

        gitService.cloneRepository(projectGitRepoUrl, directory);

        Path dir = Paths.get(projectRepoFolder);
        try {
            return Files.walk(dir)
                    .filter(path -> path.toString().endsWith("parameters.yaml") || path.endsWith("parameters.yml"))
                    .map(path -> processProject(path, path.getParent())).filter(Objects::nonNull).toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Project processProject(Path parametersFilePath, Path projectPath) {
        String projectId = projectPath.getFileName().toString();
        Log.info("processing project: " + projectId + " from file: " + parametersFilePath.toString());
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (FileInputStream inputStream = new FileInputStream(parametersFilePath.toFile())) {
            ProjectEntity projectEntity = mapper.readValue(inputStream, ProjectEntity.class);
            List<RepositoryEntity> envgeneInstanceRepos = projectEntity.repositories.stream().filter(repositoryEntity -> repositoryEntity.type.equals("envgeneInstance")).toList();
            return new Project(projectId,
                    projectEntity.projectName(),
                    ProjectType.fromString(projectEntity.type),
                    projectEntity.customerName(),
                    convertToInstanceRepositories(envgeneInstanceRepos),
                    ClusterPlatform.fromString(projectEntity.clusterPlatform()));
        } catch (IOException e) {
            Log.error("Can't read project data from file: " + parametersFilePath, e);
            return null;
        }
    }

    private List<InstanceRepository> convertToInstanceRepositories(List<RepositoryEntity> envgeneInstanceRepos) {
        return envgeneInstanceRepos.stream()
                .map(repoEntity -> new InstanceRepository(
                        UUID.randomUUID().toString(),
                        repoEntity.url(),
                        repoEntity.token()))
                .toList();
    }

    public record ProjectEntity(String projectName, String customerName, String type,
                                List<RepositoryEntity> repositories, String clusterPlatform) {
    }

    public record RepositoryEntity(String type, String url, String token) {
    }

}
