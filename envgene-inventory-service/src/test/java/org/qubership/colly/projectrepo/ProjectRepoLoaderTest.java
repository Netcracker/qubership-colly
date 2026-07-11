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
            List.of(
                    new InstanceRepository("https://gitlab.com/test/repo1.git", "main", "test-token-1", "cn"),
                    new InstanceRepository("https://gitlab.com/test/repo2.git", null, "test-token-2", "mb")
            ),
            new EnvgeneTemplateRepository("https://gitlab.com/test/templateRepo.git", "main",
                    new EnvgeneArtifact("my-app:feature-new-ui-123456", "dev")),
            List.of(new GitGroupUrl("cn", "https://gitlab.com/test-group")));

    public static final Project TEST_PROJECT_2 = new Project(
            "test-project-2",
            "Test Project 2",
            List.of(
                    new InstanceRepository("https://gitlab.com/test/repo4.git", null, "test-token-4", "cn")
            ),
            new EnvgeneTemplateRepository("https://gitlab.com/test/templateRepo2.git", "main",
                    new EnvgeneArtifact("my-app:feature-new-ui-0987654", "ci")),
            List.of());

    @InjectMock
    GitService gitService;
    @Inject
    ProjectRepoLoader loader;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            FileUtils.copyDirectory(new File("src/test/resources/" + invocation.getArgument(0)), invocation.getArgument(3));
                    return null;
                }
        ).when(gitService).cloneRepository(anyString(), any(), any(), any());
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
                name: Test Project
                customerName: Test Customer
                type: product
                clusterPlatform: ocp
                repositories:
                  - type: envgeneInstance
                    url: https://gitlab.com/test/repo1.git
                    branch: main
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
                List.of(
                        new InstanceRepository("https://gitlab.com/test/repo1.git", "main", "test-token-1", null),
                        new InstanceRepository("https://gitlab.com/test/repo2.git", null, "test-token-2", null)
                ),
                null,
                List.of());

        Project project = loader.processProject(parametersFile, projectDir);
        assertThat(project, equalTo(expectedResult));
    }

    @Test
    @TestConfigProperty(key = "colly.eis.project.repo.folder", value = "target/test-project-repo-folder")
    @TestConfigProperty(key = "colly.eis.project.repo.url", value = "test-project-repo")
    void test_parse_yaml_with_uppercase_type(@TempDir Path tempDir) throws IOException {
        String yamlContent = """
                name: Test Project
                customerName: Test Customer
                type: PRODUCT
                clusterPlatform: K8S
                repositories: []
                accessGroups: ["group1", "group2"]
                """;

        Path projectDir = tempDir.resolve("test-project");
        Files.createDirectories(projectDir);
        Path parametersFile = projectDir.resolve("parameters.yaml");
        Files.writeString(parametersFile, yamlContent);
        Project expectedResult = new Project("test-project", "Test Project", List.of(), null, List.of());

        Project project = loader.processProject(parametersFile, projectDir);

        assertThat(project, equalTo(expectedResult));
    }

    @Test
    @TestConfigProperty(key = "colly.eis.project.repo.folder", value = "target/test-project-repo-folder")
    @TestConfigProperty(key = "colly.eis.project.repo.url", value = "test-project-repo")
    void test_filter_only_envgene_instance_repos(@TempDir Path tempDir) throws IOException {
        String yamlContent = """
                name: Test Project
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
    void test_parse_yaml_with_git_group_urls(@TempDir Path tempDir) throws IOException {
        String yamlContent = """
                name: Test Project
                customerName: Test Customer
                type: product
                clusterPlatform: ocp
                repositories: []
                gitGroupUrls:
                  - region: cn
                    url: https://gitlab.com/my-group
                  - region: mb
                    url: https://gitlab.com/my-group-mb
                """;

        Path projectDir = tempDir.resolve("test-project");
        Files.createDirectories(projectDir);
        Path parametersFile = projectDir.resolve("parameters.yaml");
        Files.writeString(parametersFile, yamlContent);

        Project project = loader.processProject(parametersFile, projectDir);

        assertNotNull(project);
        assertThat(project.gitGroupUrls(), hasSize(2));
        assertThat(project.gitGroupUrls().get(0).region(), equalTo("cn"));
        assertThat(project.gitGroupUrls().get(0).url(), equalTo("https://gitlab.com/my-group"));
        assertThat(project.gitGroupUrls().get(1).region(), equalTo("mb"));
        assertThat(project.gitGroupUrls().get(1).url(), equalTo("https://gitlab.com/my-group-mb"));
    }

    @Test
    @TestConfigProperty(key = "colly.eis.project.repo.folder", value = "target/test-project-repo-folder")
    @TestConfigProperty(key = "colly.eis.project.repo.url", value = "test-project-repo")
    void test_parse_yaml_without_git_group_urls(@TempDir Path tempDir) throws IOException {
        String yamlContent = """
                name: Test Project
                customerName: Test Customer
                type: product
                clusterPlatform: ocp
                repositories: []
                """;

        Path projectDir = tempDir.resolve("test-project");
        Files.createDirectories(projectDir);
        Path parametersFile = projectDir.resolve("parameters.yaml");
        Files.writeString(parametersFile, yamlContent);

        Project project = loader.processProject(parametersFile, projectDir);

        assertNotNull(project);
        assertThat(project.gitGroupUrls(), empty());
    }

    @Test
    @TestConfigProperty(key = "colly.eis.project.repo.folder", value = "target/test-project-repo-folder")
    @TestConfigProperty(key = "colly.eis.project.repo.url", value = "test-project-repo")
    void test_parse_invalid_yaml_returns_null(@TempDir Path tempDir) throws IOException {
        String yamlContent = """
                name: Test Project
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

}
