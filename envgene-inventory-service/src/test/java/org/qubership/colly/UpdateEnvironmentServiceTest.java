package org.qubership.colly;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.qubership.colly.cloudpassport.GitInfo;
import org.qubership.colly.cloudpassport.envgen.EnvDefinition;
import org.qubership.colly.cloudpassport.envgen.ParamsetFileData;
import org.qubership.colly.db.data.*;
import org.qubership.colly.dto.CommitInfoDto;
import org.qubership.colly.dto.ParameterDto;
import org.qubership.colly.projectrepo.InstanceRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@QuarkusComponentTest
class UpdateEnvironmentServiceTest {

    public static final CommitInfoDto COMMIT_INFO = new CommitInfoDto("test", "some_user", "some_user@fff.com");
    @Inject
    UpdateEnvironmentService updateEnvironmentService;

    @Inject
    ParamsetService paramsetService;

    @Inject
    YqService yqService;

    @TempDir
    Path tempDir;

    @InjectMock
    GitService gitService;

    private Cluster testCluster;
    private Environment testEnvironment;

    @BeforeEach
    void setUp() throws IOException {
        // Copy test resources to the temp directory
        Path testResourcesPath = Path.of("src/test/resources/gitrepo_with_cloudpassports");
        if (Files.exists(testResourcesPath)) {
            copyDirectory(testResourcesPath, tempDir.resolve("gitrepo_with_cloudpassports"));
        }

        GitInfo gitInfo = new GitInfo(new InstanceRepository("gitrepo_with_cloudpassports", "main", "42", "cn"), tempDir.toString(), "1");
        testCluster = Cluster.builder().build();
        testCluster.setName("test-cluster");
        testCluster.setGitInfo(gitInfo);
        testEnvironment = new Environment("1", "env-test");
        testEnvironment.setDescription("new description");
        testEnvironment.setOwners(List.of("new owner"));
        testEnvironment.setTeams(List.of("new test-team"));
        testEnvironment.setLabels(List.of("ci", "dev"));
        testEnvironment.setType(EnvironmentType.DESIGN_TIME);
        testEnvironment.setRole("Dev");
        testEnvironment.setStatus(EnvironmentStatus.IN_USE);
        testEnvironment.setExpirationDate(LocalDate.of(2025, 12, 31));
    }

    @Test
    void updateEnvironment_shouldProcessExistingEnvironmentFile() throws IOException {
        updateEnvironmentService.updateEnvironment(testCluster, testEnvironment);

        Path path = Paths.get(testCluster.getGitInfo().folderName() + "/gitrepo_with_cloudpassports/test-cluster/env-test/Inventory/env_definition.yml");
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        EnvDefinition envDefinition = objectMapper.readValue(path.toFile(), EnvDefinition.class);
        assertEquals("new description", envDefinition.inventory().getMetadata().description());
        assertNull(envDefinition.inventory().getDescription());
        assertThat(envDefinition.inventory().getMetadata().owners(), contains("new owner"));
        assertThat(envDefinition.inventory().getMetadata().teams(), contains("new test-team"));
        assertNull(envDefinition.inventory().getOwners());
        assertThat(envDefinition.inventory().getMetadata().labels(), contains("ci", "dev"));
        assertEquals("DESIGN_TIME", envDefinition.inventory().getMetadata().type());
        assertEquals("Dev", envDefinition.inventory().getMetadata().role());
        assertThat(envDefinition.inventory().getMetadata().status(), is("IN_USE"));
        assertThat(envDefinition.inventory().getMetadata().expirationDate(), is("2025-12-31"));
        verify(gitService).commitAndPush(Paths.get(testCluster.getGitInfo().folderName()).toFile(), "Update environment " + testEnvironment.getName());
    }

    @Test
    void updateEnvironment_shouldHandleNonExistentEnvironment() {
        // Given
        Environment nonExistentEnv = new Environment("2", "non-existent-env");
        nonExistentEnv.setDescription("Non-existent environment");

        assertThrows(IllegalArgumentException.class, () -> updateEnvironmentService.updateEnvironment(testCluster, nonExistentEnv));
    }

    @Test
    void updateEnvironment_shouldHandleInvalidGitInfoPath() {
        GitInfo invalidGitInfo = new GitInfo(new InstanceRepository("test-repo", "main", "42", "cn"), "/invalid/path", "2");
        Cluster invalidCluster = Cluster.builder().build();
        invalidCluster.setName("invalid-cluster");
        invalidCluster.setGitInfo(invalidGitInfo);

        assertThrows(IllegalArgumentException.class, () -> updateEnvironmentService.updateEnvironment(invalidCluster, testEnvironment));
    }


    @Test
    void updateParamset_shouldWriteNamespaceLevelParameters() throws IOException {
        // Given
        Map<ParamsetContext, List<ParameterDto>> params = new EnumMap<>(ParamsetContext.class);
        params.put(ParamsetContext.DEPLOYMENT, List.of(new ParameterDto("NEW_DEPLOY_PARAM", "new-deploy-value")));
        params.put(ParamsetContext.RUNTIME, List.of(new ParameterDto("NEW_RUNTIME_PARAM", "new-runtime-value")));
        params.put(ParamsetContext.PIPELINE, List.of());
        CommitInfoDto commitInfo = new CommitInfoDto("my commit message", "user", "user@test.com");

        // When
        updateEnvironmentService.updateParamset(testCluster, testEnvironment,
                new ParamsetService.ParamsetTarget(ParamsetLevel.NAMESPACE, "core"), null, params, commitInfo);

        // Then
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Path inventoryDir = Paths.get(tempDir.toString(), "gitrepo_with_cloudpassports/test-cluster/env-test/Inventory");

        ParamsetFileData deployFile = mapper.readValue(
                inventoryDir.resolve("parameters/core-deploy-ui-override.yaml").toFile(),
                ParamsetFileData.class);
        ParamsetFileData expectedDeploymentParamset = new ParamsetFileData("core-deploy-ui-override", Map.of("NEW_DEPLOY_PARAM", "new-deploy-value"), List.of());
        assertThat(deployFile, equalTo(expectedDeploymentParamset));

        ParamsetFileData runtimeFile = mapper.readValue(
                inventoryDir.resolve("parameters/core-runtime-ui-override.yaml").toFile(),
                ParamsetFileData.class);
        ParamsetFileData expectedRuntimeParamset = new ParamsetFileData("core-runtime-ui-override", Map.of("NEW_RUNTIME_PARAM", "new-runtime-value"), List.of());
        assertThat(runtimeFile, equalTo(expectedRuntimeParamset));

        verify(gitService).commitAndPush(Paths.get(testCluster.getGitInfo().folderName()).toFile(), commitInfo.commitMessage(), null, commitInfo.username(), commitInfo.email());
    }

    @Test
    void updateParamset_shouldWriteEnvironmentLevelParameters() throws IOException {
        // Given
        Map<ParamsetContext, List<ParameterDto>> params = new EnumMap<>(ParamsetContext.class);
        params.put(ParamsetContext.DEPLOYMENT, List.of(new ParameterDto("ENV_DEPLOY_PARAM", "updated-env-value")));
        params.put(ParamsetContext.RUNTIME, List.of());
        params.put(ParamsetContext.PIPELINE, List.of());

        // When — commitInfo is null, so default message should be used
        updateEnvironmentService.updateParamset(testCluster, testEnvironment,
                new ParamsetService.ParamsetTarget(ParamsetLevel.ENVIRONMENT, "cloud"), null, params, COMMIT_INFO);

        // Then
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Path inventoryDir = Paths.get(tempDir.toString(), "gitrepo_with_cloudpassports/test-cluster/env-test/Inventory");

        ParamsetFileData deployFile = mapper.readValue(
                inventoryDir.resolve("parameters/deploy-ui-override.yaml").toFile(),
                ParamsetFileData.class);

        ParamsetFileData expectedDeploymentParamset = new ParamsetFileData("deploy-ui-override", Map.of("ENV_DEPLOY_PARAM", "updated-env-value"), List.of());
        assertThat(deployFile, equalTo(expectedDeploymentParamset));

        verify(gitService).commitAndPush(Paths.get(testCluster.getGitInfo().folderName()).toFile(), COMMIT_INFO.commitMessage(), null, COMMIT_INFO.username(), COMMIT_INFO.email());
    }

    @Test
    void updateParamset_shouldWriteApplicationLevelParameters() throws IOException {
        // Given
        Map<ParamsetContext, List<ParameterDto>> params = new EnumMap<>(ParamsetContext.class);
        params.put(ParamsetContext.DEPLOYMENT, List.of(new ParameterDto("MY_APP_PARAM", "new-app-value")));
        params.put(ParamsetContext.RUNTIME, List.of(new ParameterDto("MY_APP_RUNTIME_PARAM", "new-runtime-value")));
        params.put(ParamsetContext.PIPELINE, List.of());
        CommitInfoDto commitInfo = new CommitInfoDto("update my-app params", "user", "user@test.com");

        // When
        updateEnvironmentService.updateParamset(testCluster, testEnvironment,
                new ParamsetService.ParamsetTarget(ParamsetLevel.APPLICATION, "core"), "my-app", params, commitInfo);

        // Then
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Path inventoryDir = Paths.get(tempDir.toString(), "gitrepo_with_cloudpassports/test-cluster/env-test/Inventory");

        ParamsetFileData deployParamset = mapper.readValue(
                inventoryDir.resolve("parameters/core-my-app-deploy-ui-override.yaml").toFile(),
                ParamsetFileData.class);
        ParamsetFileData expectedDeploymentParamset = new ParamsetFileData(
                "core-my-app-deploy-ui-override",
                Map.of(),
                List.of(new ParamsetFileData.ParamsetApplicationData("my-app", Map.of("MY_APP_PARAM", "new-app-value"))));
        assertThat(deployParamset, equalTo(expectedDeploymentParamset));


        ParamsetFileData expectedRuntimeParamset = new ParamsetFileData(
                "core-my-app-runtime-ui-override",
                Map.of(),
                List.of(new ParamsetFileData.ParamsetApplicationData("my-app", Map.of("MY_APP_RUNTIME_PARAM", "new-runtime-value"))));
        ParamsetFileData runtimeParamset = mapper.readValue(
                inventoryDir.resolve("parameters/core-my-app-runtime-ui-override.yaml").toFile(),
                ParamsetFileData.class);
        assertThat(runtimeParamset, equalTo(expectedRuntimeParamset));

        verify(gitService).commitAndPush(Paths.get(testCluster.getGitInfo().folderName()).toFile(), commitInfo.commitMessage(), null, commitInfo.username(), commitInfo.email());
    }

    @Test
    void updateParamset_shouldAddReferencesToEnvDefinition() throws IOException {
        // Given — env-test has no 'core' deployPostfix in envTemplate
        Map<ParamsetContext, List<ParameterDto>> params = new EnumMap<>(ParamsetContext.class);
        params.put(ParamsetContext.DEPLOYMENT, List.of(new ParameterDto("CORE_DEPLOY_PARAM", "value")));
        params.put(ParamsetContext.RUNTIME, List.of(new ParameterDto("CORE_RUNTIME_PARAM", "value")));
        params.put(ParamsetContext.PIPELINE, List.of());

        // When
        updateEnvironmentService.updateParamset(testCluster, testEnvironment,
                new ParamsetService.ParamsetTarget(ParamsetLevel.NAMESPACE, "core"), null, params, COMMIT_INFO);

        // Then
        Path envDefPath = Paths.get(tempDir.toString(), "gitrepo_with_cloudpassports/test-cluster/env-test/Inventory/env_definition.yml");
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        EnvDefinition envDefinition = mapper.readValue(envDefPath.toFile(), EnvDefinition.class);

        assertThat(envDefinition.envTemplate().envSpecificParamsets().get("core"),
                contains("core-deploy-ui-override"));
        assertThat(envDefinition.envTemplate().envSpecificTechnicalParamsets().get("core"),
                contains("core-runtime-ui-override"));
        // pipeline was empty — no reference added
        assertNull(envDefinition.envTemplate().envSpecificE2EParamsets().get("core"));
        // existing 'bss' references unchanged
        assertThat(envDefinition.envTemplate().envSpecificParamsets().get("bss"),
                contains("bss-deploy-ui-override", "bss-my-app-deploy-ui-override"));
    }

    @Test
    void updateParamset_shouldNotDuplicateReferenceOnRepeatCall() throws IOException {
        // Given
        Map<ParamsetContext, List<ParameterDto>> params = new EnumMap<>(ParamsetContext.class);
        params.put(ParamsetContext.DEPLOYMENT, List.of(new ParameterDto("CORE_DEPLOY_PARAM", "value")));
        params.put(ParamsetContext.RUNTIME, List.of());
        params.put(ParamsetContext.PIPELINE, List.of());

        // When — call twice with same params
        updateEnvironmentService.updateParamset(testCluster, testEnvironment,
                new ParamsetService.ParamsetTarget(ParamsetLevel.NAMESPACE, "core"), null, params, COMMIT_INFO);
        updateEnvironmentService.updateParamset(testCluster, testEnvironment,
                new ParamsetService.ParamsetTarget(ParamsetLevel.NAMESPACE, "core"), null, params, COMMIT_INFO);

        // Then — reference appears exactly once
        Path envDefPath = Paths.get(tempDir.toString(), "gitrepo_with_cloudpassports/test-cluster/env-test/Inventory/env_definition.yml");
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        EnvDefinition envDefinition = mapper.readValue(envDefPath.toFile(), EnvDefinition.class);

        List<String> coreDeployRefs = envDefinition.envTemplate().envSpecificParamsets().get("core");
        assertThat(coreDeployRefs, contains("core-deploy-ui-override"));
    }

    @Test
    void updateParamset_shouldCreateEnvSpecificE2EParamsetsCloudSectionIfMissing() throws IOException {
        // Given — overwrite env_definition.yml without envSpecificE2EParamsets section
        Path envDefPath = Paths.get(tempDir.toString(),
                "gitrepo_with_cloudpassports/test-cluster/env-test/Inventory/env_definition.yml");
        Files.writeString(envDefPath, """
                inventory:
                  environmentName: "env-test"
                  description: "some env for tests"
                envTemplate:
                  envSpecificParamsets:
                    cloud:
                      - deploy-ui-override
                  envSpecificTechnicalParamsets:
                    cloud:
                      - runtime-ui-override
                """);

        Map<ParamsetContext, List<ParameterDto>> params = new EnumMap<>(ParamsetContext.class);
        params.put(ParamsetContext.DEPLOYMENT, null);
        params.put(ParamsetContext.RUNTIME, null);
        params.put(ParamsetContext.PIPELINE, List.of(new ParameterDto("PIPELINE_PARAM", "value")));

        // When
        updateEnvironmentService.updateParamset(testCluster, testEnvironment,
                new ParamsetService.ParamsetTarget(ParamsetLevel.ENVIRONMENT, "cloud"), null, params, COMMIT_INFO);

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        EnvDefinition envDefinition = mapper.readValue(envDefPath.toFile(), EnvDefinition.class);
        assertThat(envDefinition.envTemplate().envSpecificE2EParamsets().get("cloud"),
                contains("pipeline-ui-override"));
        // Other sections untouched
        assertThat(envDefinition.envTemplate().envSpecificParamsets().get("cloud"),
                contains("deploy-ui-override"));
        assertThat(envDefinition.envTemplate().envSpecificTechnicalParamsets().get("cloud"),
                contains("runtime-ui-override"));
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(sourcePath -> {
            try {
                Path targetPath = target.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy " + sourcePath, e);
            }
        });
    }
}
