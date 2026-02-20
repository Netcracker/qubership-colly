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
import org.qubership.colly.db.data.EnvironmentStatus;
import org.qubership.colly.db.data.EnvironmentType;
import org.qubership.colly.db.data.ParamsetContext;
import org.qubership.colly.db.data.ParamsetLevel;
import org.qubership.colly.projectrepo.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
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
                            null,
                            List.of(),
                            List.of(),
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
                            "cm",
                            List.of("group1", "group2"),
                            List.of("group1", "group2", "group3"),
                            List.of(
                                    new Paramset(ParamsetContext.DEPLOYMENT, ParamsetLevel.NAMESPACE, "core", null, Map.of("CORE_DEPLOY_PARAMETER", "some value")),
                                    new Paramset(ParamsetContext.DEPLOYMENT, ParamsetLevel.APPLICATION, "core", "my-app", Map.of("MY_APP_DEPLOY_PARAMETER", "foo")),
                                    new Paramset(ParamsetContext.DEPLOYMENT, ParamsetLevel.ENVIRONMENT, "cloud", null, Map.of("ENV_DEPLOY_PARAMETER", "some value")),
                                    new Paramset(ParamsetContext.RUNTIME, ParamsetLevel.NAMESPACE, "core", null, Map.of("CORE_RUNTIME_PARAMETER", "some value3")),
                                    new Paramset(ParamsetContext.RUNTIME, ParamsetLevel.APPLICATION, "core", "my-app", Map.of("MY_APP_RUNTIME_PARAMETER", "bar")),
                                    new Paramset(ParamsetContext.RUNTIME, ParamsetLevel.ENVIRONMENT, "cloud", null, Map.of("ENV_RUNTIME_PARAMETER", "some value")),
                                    new Paramset(ParamsetContext.PIPELINE, ParamsetLevel.NAMESPACE, "core", null, Map.of("CORE_PIPELINE_PARAMETER", "some value2")),
                                    new Paramset(ParamsetContext.PIPELINE, ParamsetLevel.ENVIRONMENT, "cloud", null, Map.of("ENV_PIPELINE_PARAMETER", "some value"))
                            ))),
            "http://localhost:8428",
            new GitInfo(new InstanceRepository("gitrepo_with_cloudpassports", "main", "42", "cn"),
                    "target/test-cloud-passport-folder/1", "1"),
            "https://dashboard.example.com",
            "https://dbaas.example.com",
            "https://deployer.example.com",
            "https://argo.example.com",
            "https://achka.example.com");
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
                    null,
                    List.of(),
                    List.of(),
                    List.of())),
            "https://vmsingle-victoria.unreachable.url",
            new GitInfo(new InstanceRepository("gitrepo_with_unreachable_cluster", "main", "43", "mb"), "target/test-cloud-passport-folder/2", "2"),
            null,
            null,
            null,
            null,
            "https://ach-kubernetes-agent-devops-toolkit.unreachable.url"
    );

    @InjectMock
    GitService gitService;

    @Inject
    CloudPassportLoader loader;

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
        Project project1 = new Project("1", "project-1", ProjectType.PROJECT, "some-customer",
                List.of(new InstanceRepository("gitrepo_with_cloudpassports", "main", "42", "cn")), List.of(), ClusterPlatform.K8S,
                new EnvgeneTemplateRepository("gitrepo_template", "44", "main", new EnvgeneArtifact("my-app:feature-new-ui-123456", "dev")), List.of(), null, null);
        Project project2 = new Project("2", "project-2", ProjectType.PROJECT, "some-customer",
                List.of(new InstanceRepository("gitrepo_with_unreachable_cluster", "main", "43", "mb")), List.of(), ClusterPlatform.K8S,
                new EnvgeneTemplateRepository("gitrepo_template2", "45", "main", new EnvgeneArtifact("my-app:feature-new-ui-09876", "qa")), List.of("group1", "group2"), null, null);
        List<CloudPassport> result = loader.loadCloudPassports(List.of(project1, project2));
        assertThat(result, containsInAnyOrder(TEST_CLUSTER, UNREACHABLE_CLUSTER));

    }

    @Test
    @TestConfigProperty(key = "colly.eis.cloud.passport.folder", value = "/nonexistent/path")
    void load_cloud_passports_from_test_folder_with_empty_folder() {
        List<CloudPassport> result = loader.loadCloudPassports(new ArrayList<>());
        assertTrue(result.isEmpty());
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
                null, null, null);

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
                null, null, null);

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
