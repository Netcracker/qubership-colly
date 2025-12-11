package org.qubership.colly.projectrepo;

import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.qubership.colly.GitService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@QuarkusComponentTest
class ProjectRepoLoaderTest {

    public static final Project TEST_PROJECT_1 = new Project(
            "test-project-1",
            "Test Project 1",
            ProjectType.PRODUCT,
            "Test Customer 1",
            List.of(
                    new InstanceRepository("https://gitlab.com/test/repo1.git", "https://gitlab.com/test/repo1.git", "test-token-1"),
                    new InstanceRepository("https://gitlab.com/test/repo2.git", "https://gitlab.com/test/repo2.git", "test-token-2")
            ),
            List.of(),
            ClusterPlatform.OCP
    );
    public static final Project TEST_PROJECT_2 = new Project(
            "test-project-2",
            "Test Project 2",
            ProjectType.PROJECT,
            "Test Customer 2",
            List.of(
                    new InstanceRepository("https://gitlab.com/test/repo4.git", "https://gitlab.com/test/repo4.git", "test-token-4")
            ),
            List.of(),
            ClusterPlatform.K8S
    );
    @InjectMock
    GitService gitService;
    @Inject
    ProjectRepoLoader loader;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
                    FileUtils.copyDirectory(new File("src/test/resources/" + invocation.getArgument(0)), invocation.getArgument(1));
                    return null;
                }
        ).when(gitService).cloneRepository(anyString(), any());
    }

    @Test
    @TestConfigProperty(key = "colly.eis.project.repo.folder", value = "target/test-project-repo-folder")
    @TestConfigProperty(key = "colly.eis.project.repo.url", value = "test-project-repo")
    void load_projects_from_test_folder() {
        List<Project> result = loader.loadProjects();

        assertThat(result, containsInAnyOrder(TEST_PROJECT_1, TEST_PROJECT_2));
    }


    @Test
    @Disabled("Disabled until we support handling such cases")
    @TestConfigProperty(key = "colly.eis.project.repo.folder", value = "/nonexistent/path")
    @TestConfigProperty(key = "colly.eis.project.repo.url", value = "test-project-repo")
    void load_projects_from_nonexistent_folder_returns_empty_list() {
        List<Project> result = loader.loadProjects();
        assertTrue(result.isEmpty());
    }

    @Test
    @TestConfigProperty(key = "colly.eis.project.repo.folder", value = "target/test-project-repo-folder")
    @TestConfigProperty(key = "colly.eis.project.repo.url", value = "test-project-repo")
    void test_parse_valid_yaml_with_lowercase_type(@TempDir Path tempDir) throws IOException {
        String yamlContent = """
                projectName: Test Project
                customerName: Test Customer
                type: product
                clusterPlatform: ocp
                repositories:
                  - type: envgeneInstance
                    url: https://gitlab.com/test/repo1.git
                    token: test-token-1
                  - type: envgeneInstance
                    url: https://gitlab.com/test/repo2.git
                    token: test-token-2
                  - type: other
                    url: https://gitlab.com/test/repo3.git
                    token: test-token-3
                """;
        Path projectDir = tempDir.resolve("test-project");
        Files.createDirectories(projectDir);
        Path parametersFile = projectDir.resolve("parameters.yaml");
        Files.writeString(parametersFile, yamlContent);

        Project expectedResult = new Project("test-project",
                "Test Project",
                ProjectType.PRODUCT,
                "Test Customer",
                List.of(
                        new InstanceRepository("https://gitlab.com/test/repo1.git", "https://gitlab.com/test/repo1.git", "test-token-1"),
                        new InstanceRepository("https://gitlab.com/test/repo2.git", "https://gitlab.com/test/repo2.git", "test-token-2")
                ),
                List.of(),
                ClusterPlatform.OCP);

        Project project = loader.processProject(parametersFile, projectDir);
        assertThat(project, equalTo(expectedResult));
    }

    @Test
    @TestConfigProperty(key = "colly.eis.project.repo.folder", value = "target/test-project-repo-folder")
    @TestConfigProperty(key = "colly.eis.project.repo.url", value = "test-project-repo")
    void test_parse_yaml_with_uppercase_type(@TempDir Path tempDir) throws IOException {
        String yamlContent = """
                projectName: Test Project
                customerName: Test Customer
                type: PRODUCT
                clusterPlatform: K8S
                repositories: []
                """;

        Path projectDir = tempDir.resolve("test-project");
        Files.createDirectories(projectDir);
        Path parametersFile = projectDir.resolve("parameters.yaml");
        Files.writeString(parametersFile, yamlContent);
        Project expectedResult = new Project("test-project", "Test Project", ProjectType.PRODUCT, "Test Customer", List.of(), List.of(), ClusterPlatform.K8S);

        Project project = loader.processProject(parametersFile, projectDir);

        assertThat(project, equalTo(expectedResult));
    }

    @Test
    @TestConfigProperty(key = "colly.eis.project.repo.folder", value = "target/test-project-repo-folder")
    @TestConfigProperty(key = "colly.eis.project.repo.url", value = "test-project-repo")
    void test_filter_only_envgene_instance_repos(@TempDir Path tempDir) throws IOException {
        String yamlContent = """
                projectName: Test Project
                customerName: Test Customer
                type: product
                clusterPlatform: ocp
                repositories:
                  - type: envgeneInstance
                    url: https://gitlab.com/test/repo1.git
                    token: token1
                  - type: gitops
                    url: https://gitlab.com/test/repo2.git
                    token: token2
                  - type: envgeneInstance
                    url: https://gitlab.com/test/repo3.git
                    token: token3
                  - type: terraform
                    url: https://gitlab.com/test/repo4.git
                    token: token4
                """;

        Path projectDir = tempDir.resolve("test-project");
        Files.createDirectories(projectDir);
        Path parametersFile = projectDir.resolve("parameters.yaml");
        Files.writeString(parametersFile, yamlContent);

        Project project = loader.processProject(parametersFile, projectDir);

        assertNotNull(project);
        assertThat(project.instanceRepositories(), hasSize(2));

        List<String> urls = project.instanceRepositories().stream()
                .map(InstanceRepository::url)
                .toList();
        assertThat(urls, containsInAnyOrder(
                "https://gitlab.com/test/repo1.git",
                "https://gitlab.com/test/repo3.git"
        ));
    }

    @Test
    @TestConfigProperty(key = "colly.eis.project.repo.folder", value = "target/test-project-repo-folder")
    @TestConfigProperty(key = "colly.eis.project.repo.url", value = "test-project-repo")
    void test_parse_invalid_yaml_returns_null(@TempDir Path tempDir) throws IOException {
        String yamlContent = """
                projectName: Test Project
                this is not valid yaml!!!
                type: product
                """;

        Path projectDir = tempDir.resolve("test-project");
        Files.createDirectories(projectDir);
        Path parametersFile = projectDir.resolve("parameters.yaml");
        Files.writeString(parametersFile, yamlContent);

        Project project = loader.processProject(parametersFile, projectDir);

        assertNull(project);
    }

    @Test
    @TestConfigProperty(key = "colly.eis.project.repo.folder", value = "target/test-project-repo-folder")
    @TestConfigProperty(key = "colly.eis.project.repo.url", value = "test-project-repo")
    void test_parse_yaml_with_missing_type_required_field_returns_null(@TempDir Path tempDir) throws IOException {
        String yamlContent = """
                projectName: Test Project
                customerName: Test Customer
                clusterPlatform: ocp
                repositories: []
                """;

        Path projectDir = tempDir.resolve("test-project");
        Files.createDirectories(projectDir);
        Path parametersFile = projectDir.resolve("parameters.yaml");
        Files.writeString(parametersFile, yamlContent);

        Project project = loader.processProject(parametersFile, projectDir);

        assertNull(project);
    }

    @Test
    @TestConfigProperty(key = "colly.eis.project.repo.folder", value = "target/test-project-repo-folder")
    @TestConfigProperty(key = "colly.eis.project.repo.url", value = "test-project-repo")
    void test_parse_yaml_with_missing_platform_required_field_returns_null(@TempDir Path tempDir) throws IOException {
        String yamlContent = """
                projectName: Test Project
                customerName: Test Customer
                type: product
                repositories: []
                """;

        Path projectDir = tempDir.resolve("test-project");
        Files.createDirectories(projectDir);
        Path parametersFile = projectDir.resolve("parameters.yaml");
        Files.writeString(parametersFile, yamlContent);

        Project project = loader.processProject(parametersFile, projectDir);

        assertNull(project);
    }
}
