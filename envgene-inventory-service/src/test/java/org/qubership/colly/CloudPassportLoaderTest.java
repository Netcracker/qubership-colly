package org.qubership.colly;

import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.qubership.colly.cloudpassport.*;
import org.qubership.colly.cloudpassport.envgen.CloudData;
import org.qubership.colly.cloudpassport.envgen.CloudPassportData;
import org.qubership.colly.db.data.*;
import org.qubership.colly.projectrepo.EnvgeneArtifact;
import org.qubership.colly.projectrepo.EnvgeneTemplateRepository;
import org.qubership.colly.projectrepo.InstanceRepository;
import org.qubership.colly.projectrepo.Project;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@QuarkusComponentTest
class CloudPassportLoaderTest {

    private static final CloudPassport TEST_CLUSTER = new CloudPassport("test-cluster",
            "some_token_for_test_cluster",
            "https://1E4A399FCB54F505BBA05320EADF0DB3.gr7.eu-west-1.eks.amazonaws.com:443",
            "gr7.eu-west-1.eks.amazonaws.com",
            Set.of(new CloudPassportEnvironment(
                            "env-test",
                            "some env for tests",
                            List.of(new CloudPassportNamespace("demo-k8s", "bss")),
                            List.of("test-owner"),
                            List.of(),
                            List.of(),
                            EnvironmentStatus.FREE,
                            null,
                            EnvironmentType.ENVIRONMENT,
                            null,
                            List.of(),
                            List.of(),
                            List.of(new Paramset(ParamsetContext.DEPLOYMENT, ParamsetLevel.NAMESPACE, "bss", null, Map.of("CORE_DEPLOY_PARAMETER", "some value"), "bss-deploy-ui-override")),
                            false,
                            CmApproach.NO_CMDB,
                            List.of()),
                    new CloudPassportEnvironment(
                            "env-metadata-test",
                            "description from metadata",
                            List.of(new CloudPassportNamespace("test-bss", "bss"),
                                    new CloudPassportNamespace("test-ns", "core")),
                            List.of("owner from metadata"),
                            List.of("label1", "label2"), List.of("team-from-metadata"),
                            EnvironmentStatus.IN_USE,
                            LocalDate.of(2025, 12, 31),
                            EnvironmentType.DESIGN_TIME,
                            "QA",
                            List.of("group1", "group2"),
                            List.of("group1", "group2", "group3"),
                            List.of(
                                    new Paramset(ParamsetContext.DEPLOYMENT, ParamsetLevel.NAMESPACE, "core", null, Map.of("CORE_DEPLOY_PARAMETER", "some value", "CORE_DEPLOY_PARAMETER_2", Map.of("SECOND_LEVEL_KEY", "some value")), "core-deploy-ui-override"),
                                    new Paramset(ParamsetContext.DEPLOYMENT, ParamsetLevel.APPLICATION, "core", "my-app", Map.of("MY_APP_DEPLOY_PARAMETER", "foo"), "core-my-app-deploy-ui-override"),
                                    new Paramset(ParamsetContext.DEPLOYMENT, ParamsetLevel.NAMESPACE, "core", null, Map.of("GENERIC_NAMESPACE_PARAM", "namespace value"), "core-mixed-paramset"),
                                    new Paramset(ParamsetContext.DEPLOYMENT, ParamsetLevel.APPLICATION, "core", "my-app", Map.of("GENERIC_APP_PARAM", "app value"), "core-mixed-paramset"),
                                    new Paramset(ParamsetContext.DEPLOYMENT, ParamsetLevel.NAMESPACE, "core", null, Map.of("PARAM", "namespace value"), "mixed-paramset-same-parameter"),
                                    new Paramset(ParamsetContext.DEPLOYMENT, ParamsetLevel.APPLICATION, "core", "my-app", Map.of("PARAM", "app value"), "mixed-paramset-same-parameter"),
                                    new Paramset(ParamsetContext.DEPLOYMENT, ParamsetLevel.NAMESPACE, "core", null, Map.of("DUPLICATE_PARAM", "first value"), "core-first-param"),
                                    new Paramset(ParamsetContext.DEPLOYMENT, ParamsetLevel.NAMESPACE, "core", null, Map.of("DUPLICATE_PARAM", "second value"), "core-second-param"),
                                    new Paramset(ParamsetContext.DEPLOYMENT, ParamsetLevel.APPLICATION, "core", "my-second-app", Map.of("MY_APP_DEPLOY_PARAMETER", "bar2"), "core-my-second-app-deploy-ui-override"),
                                    new Paramset(ParamsetContext.DEPLOYMENT, ParamsetLevel.ENVIRONMENT, "cloud", null, Map.of("ENV_DEPLOY_PARAMETER", "some value"), "deploy-ui-override"),
                                    new Paramset(ParamsetContext.RUNTIME, ParamsetLevel.NAMESPACE, "core", null, Map.of("CORE_RUNTIME_PARAMETER", "some value3"), "core-runtime-ui-override"),
                                    new Paramset(ParamsetContext.RUNTIME, ParamsetLevel.APPLICATION, "core", "my-app", Map.of("MY_APP_RUNTIME_PARAMETER", "bar"), "core-my-app-runtime-ui-override"),
                                    new Paramset(ParamsetContext.RUNTIME, ParamsetLevel.APPLICATION, "core", "my-app", Map.of("MY_APP_RUNTIME_PARAMETER", "barManual"), "core-my-app-runtime-manual-params"),
                                    new Paramset(ParamsetContext.RUNTIME, ParamsetLevel.ENVIRONMENT, "cloud", null, Map.of("ENV_RUNTIME_PARAMETER", "some value"), "runtime-ui-override"),
                                    new Paramset(ParamsetContext.PIPELINE, ParamsetLevel.NAMESPACE, "core", null, Map.of("CORE_PIPELINE_PARAMETER", "some value2"), "core-pipeline-ui-override"),
                                    new Paramset(ParamsetContext.PIPELINE, ParamsetLevel.ENVIRONMENT, "cloud", null, Map.of("ENV_PIPELINE_PARAMETER", "some value"), "pipeline-ui-override")
                            ),
                            true,
                            CmApproach.CMDB,
                            List.of(new SdApplication("MONITORING:0.64.1", "core"),
                                    new SdApplication("postgres:1.32.6", "core"),
                                    new SdApplication("postgres-services:1.32.6", "core"),
                                    new SdApplication("postgres:1.32.6", "postgresql-dbaas")))),
            "http://localhost:8428",
            new GitInfo(new InstanceRepository("gitrepo_with_cloudpassports", "main", "42", "cn"),
                    "target/test-cloud-passport-folder/1", "1"),
            "https://dashboard.example.com",
            "https://dbaas.example.com",
            "https://deployer.example.com",
            "https://argo.example.com",
            "https://achka.example.com",
            "cm");
    private static final CloudPassport UNREACHABLE_CLUSTER = new CloudPassport("unreachable-cluster",
            "1234567890",
            "https://some.unreachable.url:8443",
            "unreachable.url",
            Set.of(new CloudPassportEnvironment(
                    "env-1",
                    "some env for tests",
                    List.of(new CloudPassportNamespace("namespace-2", "bss"), new CloudPassportNamespace("namespace-1", "core")),
                    List.of(),
                    List.of(),
                    List.of(),
                    EnvironmentStatus.FREE,
                    null,
                    EnvironmentType.ENVIRONMENT,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    false,
                    CmApproach.NO_CMDB,
                    List.of())),
            "https://vmsingle-victoria.unreachable.url",
            new GitInfo(new InstanceRepository("gitrepo_with_unreachable_cluster", "main", "43", "mb"), "target/test-cloud-passport-folder/2", "2"),
            null,
            null,
            null,
            null,
            "https://ach-kubernetes-agent-devops-toolkit.unreachable.url",
            null
    );

    @InjectMock
    GitService gitService;

    @Inject
    CloudPassportLoader loader;

    @Inject
    ParamsetService paramsetService;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            FileUtils.copyDirectory(new File("src/test/resources/" + invocation.getArgument(0)), invocation.getArgument(3));
                    return null;
                }
        ).when(gitService).cloneRepository(anyString(), any(), any(), any());

    }


    @Test
    @TestConfigProperty(key = "colly.eis.cloud.passport.folder", value = "target/test-cloud-passport-folder")
    void load_cloud_passports_from_test_folder() {
        Project project1 = new Project("1", "project-1",
                List.of(new InstanceRepository("gitrepo_with_cloudpassports", "main", "42", "cn")),
                new EnvgeneTemplateRepository("gitrepo_template", "main", new EnvgeneArtifact("my-app:feature-new-ui-123456", "dev")),
                List.of());
        Project project2 = new Project("2", "project-2",
                List.of(new InstanceRepository("gitrepo_with_unreachable_cluster", "main", "43", "mb")),
                new EnvgeneTemplateRepository("gitrepo_template2", "main", new EnvgeneArtifact("my-app:feature-new-ui-09876", "qa")),
                List.of());
        List<CloudPassport> result = loader.loadCloudPassports(List.of(project1, project2));
        assertThat(result, containsInAnyOrder(TEST_CLUSTER, UNREACHABLE_CLUSTER));

    }

    @Test
    @TestConfigProperty(key = "colly.eis.cloud.passport.folder", value = "target/test-cloud-passport-folder")
    void load_cloud_passports_from_test_folder_with_empty_folder() {
        Project project1 = new Project("1", "project-1",
                List.of(new InstanceRepository("gitrepo_with_cloudpassports_invalid_cases", "main", "42", "cn")),
                new EnvgeneTemplateRepository("gitrepo_template", "main", new EnvgeneArtifact("my-app:feature-new-ui-123456", "dev")),
                List.of());

        List<CloudPassport> result = loader.loadCloudPassports(List.of(project1));
        assertThat(result, contains(new CloudPassport("cluster-with-invalid-envs",
                "some_token_for_cluster_with_invalid_envs",
                "https://42.gr7.eu-west-1.eks.amazonaws.com:443",
                "gr7.eu-west-1.eks.amazonaws.com",
                Set.of(new CloudPassportEnvironment("invalid-yaml-namespace", null, List.of(), List.of(), List.of(), List.of(), EnvironmentStatus.FREE, null, EnvironmentType.ENVIRONMENT, null, List.of(), List.of(), List.of(), false, CmApproach.NO_CMDB, List.of()),
                        new CloudPassportEnvironment("env-from-folder-name", "env with name derived from folder", List.of(), List.of(), List.of(), List.of(), EnvironmentStatus.FREE, null, EnvironmentType.ENVIRONMENT, null, List.of(), List.of(), List.of(), false, CmApproach.NO_CMDB, List.of())),
                "http://localhost:8428",
                new GitInfo(new InstanceRepository("gitrepo_with_cloudpassports_invalid_cases", "main", "42", "cn"),
                        "target/test-cloud-passport-folder/1", "1"),
                "https://dashboard.example.com",
                "https://dbaas.example.com",
                "https://deployer.example.com",
                "https://argo.example.com",
                "https://ach-kubernetes-agent-devops-toolkit.gr7.eu-west-1.eks.amazonaws.com",
                null
        )));
    }


    @Test
    @TestConfigProperty(key = "colly.eis.cloud.passport.folder", value = "target/test-cloud-passport-folder")
    void ssp_standalone_is_read_from_metadata() {
        Project project = new Project("1", "project-1",
                List.of(new InstanceRepository("gitrepo_with_cloudpassports", "main", "42", "cn")),
                new EnvgeneTemplateRepository("gitrepo_template", "main", new EnvgeneArtifact("my-app:1.0", "dev")),
                List.of());

        List<CloudPassport> result = loader.loadCloudPassports(List.of(project));

        Map<String, Boolean> sspByEnv = result.stream()
                .flatMap(cp -> cp.environments().stream())
                .collect(java.util.stream.Collectors.toMap(
                        CloudPassportEnvironment::name,
                        CloudPassportEnvironment::sspStandalone));

        assertTrue(sspByEnv.get("env-metadata-test"), "env-metadata-test should have sspStandalone=true");
        assertFalse(sspByEnv.get("env-test"), "env-test should have sspStandalone=false when absent from metadata");
    }

    @Test
    @TestConfigProperty(key = "colly.eis.cloud.passport.folder", value = "target/test-cloud-passport-folder")
    void cm_approach_is_cmdb_when_inventory_deployer_is_present() {
        Project project = new Project("1", "project-1",
                List.of(new InstanceRepository("gitrepo_with_cloudpassports", "main", "42", "cn")),
                new EnvgeneTemplateRepository("gitrepo_template", "main", new EnvgeneArtifact("my-app:1.0", "dev")),
                List.of());

        List<CloudPassport> result = loader.loadCloudPassports(List.of(project));

        Map<String, CmApproach> cmApproachByEnv = result.stream()
                .flatMap(cp -> cp.environments().stream())
                .collect(java.util.stream.Collectors.toMap(
                        CloudPassportEnvironment::name,
                        CloudPassportEnvironment::cmApproach));

        assertEquals(CmApproach.CMDB, cmApproachByEnv.get("env-metadata-test"), "env-metadata-test has inventory.deployer so cmApproach should be cmdb");
        assertEquals(CmApproach.NO_CMDB, cmApproachByEnv.get("env-test"), "env-test has no inventory.deployer so cmApproach should be noCmdb");
    }

    @Test
    @TestConfigProperty(key = "colly.eis.cloud.passport.folder", value = "target/test-cloud-passport-folder")
    void envs_outside_environments_folder_are_ignored() {
        Project project = new Project("1", "project-1",
                List.of(new InstanceRepository("gitrepo_with_cloudpassports_invalid_cases", "main", "42", "cn")),
                new EnvgeneTemplateRepository("gitrepo_template", "main", new EnvgeneArtifact("my-app:feature-new-ui-123456", "dev")),
                List.of());

        List<CloudPassport> result = loader.loadCloudPassports(List.of(project));

        List<String> clusterNames = result.stream().map(CloudPassport::name).toList();
        assertThat(clusterNames, not(hasItem("cluster-outside-environments")));
    }

    @Test
    @TestConfigProperty(key = "colly.eis.cloud.passport.folder", value = "target/test-cloud-passport-folder")
    void no_environments_folder_in_repo() {
        Project project = new Project("1", "project-1",
                List.of(new InstanceRepository("gitrepo_with_cloudpassports_invalid_root_folder", "main", "42", "cn")),
                null,
                List.of());

        List<CloudPassport> result = loader.loadCloudPassports(List.of(project));

        List<String> clusterNames = result.stream().map(CloudPassport::name).toList();
        assertThat(clusterNames, not(hasItem("cluster-outside-environments")));
    }

    @Test
    void test_read_cloud_passport_data(@TempDir Path tempDir) throws IOException {
        String yaml = """
                cloud:
                  CLOUD_API_HOST: "api.example.com"
                  CLOUD_API_PORT: 443
                  CLOUD_PROTOCOL: "https"
                  CLOUD_DEPLOY_TOKEN: "tokenKey"
                  CLOUD_DASHBOARD_URL: https://dashboard.example.com
                  CMDB_URL: https://deployer.example.com
                cse:
                  MONITORING_NAMESPACE: "monitoring"
                  MONITORING_TYPE: "VictoriaDB"
                  MONITORING_EXT_MONITORING_QUERY_URL: "http://monitoring.example.com"
                dbaas:
                  API_DBAAS_ADDRESS: https://dbaas.example.com
                devops:
                  ARGOCD_URL: https://argo.example.com
                """;
        Path file = tempDir.resolve("data.yml");
        Files.writeString(file, yaml);

        CloudPassportData result = loader.parseCloudPassportDataFile(file);
        assertNotNull(result);
        assertThat(result.cloud().cloudApiHost(), equalTo("api.example.com"));
        assertThat(result.cloud().cloudApiPort(), equalTo("443"));
        assertThat(result.cloud().cloudProtocol(), equalTo("https"));
        assertThat(result.cloud().cloudCmdbUrl(), equalTo("https://deployer.example.com"));

        assertThat(result.cse().monitoringNamespace(), equalTo("monitoring"));
        assertThat(result.cse().monitoringType(), equalTo("VictoriaDB"));
        assertThat(result.cse().monitoringExtMonitoringQueryUrl(), equalTo("http://monitoring.example.com"));

        assertThat(result.dbaas().apiDBaaSAddress(), equalTo("https://dbaas.example.com"));
        assertThat(result.devops().argocdUrl(), equalTo("https://argo.example.com"));

    }

    @Test
    void testParseTokenFromCredsFile_validYaml(@TempDir Path tempDir) throws IOException {
        CloudData cloud = new CloudData(null, null, "tokenKey", null,
                null, null, null, null);

        CloudPassportData passportData = new CloudPassportData(cloud, null, null, null);

        String credsYaml = """
                tokenKey:
                  secret: "topsecret"
                """;
        Path credsFile = tempDir.resolve("creds.yml");
        Files.writeString(credsFile, credsYaml);

        String token = loader.parseTokenFromCredsFile(credsFile, passportData);
        assertEquals("topsecret", token);
    }

    @Test
    void testParseTokenFromCredsFile_missingSecretThrows(@TempDir Path tempDir) throws IOException {
        CloudData cloud = new CloudData(null, null, "missingKey", null,
                null, null, null, null);

        CloudPassportData passportData = new CloudPassportData(cloud, null, null, null);

        String yaml = """
                anotherKey:
                  secret: "data"
                """;
        Path file = tempDir.resolve("creds.yml");
        Files.writeString(file, yaml);

        assertThrows(RuntimeException.class, () -> loader.parseTokenFromCredsFile(file, passportData));
    }
}
