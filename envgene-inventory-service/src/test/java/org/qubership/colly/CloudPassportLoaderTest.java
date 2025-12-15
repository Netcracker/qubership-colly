package org.qubership.colly;

import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.qubership.colly.cloudpassport.CloudPassport;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;
import org.qubership.colly.cloudpassport.CloudPassportNamespace;
import org.qubership.colly.cloudpassport.GitInfo;
import org.qubership.colly.cloudpassport.envgen.CloudData;
import org.qubership.colly.cloudpassport.envgen.CloudPassportData;
import org.qubership.colly.db.data.EnvironmentStatus;
import org.qubership.colly.db.data.EnvironmentType;
import org.qubership.colly.projectrepo.ClusterPlatform;
import org.qubership.colly.projectrepo.InstanceRepository;
import org.qubership.colly.projectrepo.Project;
import org.qubership.colly.projectrepo.ProjectType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
            Set.of(new CloudPassportEnvironment(
                            "env-test",
                            "some env for tests",
                            List.of(new CloudPassportNamespace("demo-k8s")),
                            List.of("test-owner"),
                            List.of(),
                            List.of(),
                            EnvironmentStatus.FREE,
                            null,
                            EnvironmentType.ENVIRONMENT,
                            null,
                            null),
                    new CloudPassportEnvironment(
                            "env-metadata-test",
                            "description from metadata",
                            List.of(new CloudPassportNamespace("test-ns")),
                            List.of("owner from metadata"),
                            List.of("label1", "label2"), List.of("team-from-metadata"),
                            EnvironmentStatus.IN_USE,
                            LocalDate.of(2025, 12, 31),
                            EnvironmentType.DESIGN_TIME,
                            "QA",
                            "cm")),
            "http://localhost:8428",
            new GitInfo(new InstanceRepository("gitrepo_with_cloudpassports", "gitrepo_with_cloudpassports", "42"),
                    "target/test-cloud-passport-folder/1"),
            "https://dashboard.example.com",
            "https://dbaas.example.com",
            "https://deployer.example.com",
            "https://argo.example.com");
    private static final CloudPassport UNREACHABLE_CLUSTER = new CloudPassport("unreachable-cluster",
            "1234567890",
            "https://some.unreachable.url:8443",
            Set.of(new CloudPassportEnvironment(
                    "env-1",
                    "some env for tests",
                    List.of(new CloudPassportNamespace("namespace-2"), new CloudPassportNamespace("namespace-1")),
                    List.of(),
                    List.of(),
                    List.of(),
                    EnvironmentStatus.FREE,
                    null,
                    EnvironmentType.ENVIRONMENT,
                    null,
                    null)),
            "http://vmsingle-k8s.victoria:8429",
            new GitInfo(new InstanceRepository("gitrepo_with_unreachable_cluster", "gitrepo_with_unreachable_cluster", "43"), "target/test-cloud-passport-folder/2"),
            null,
            null,
            null,
            null
    );

    @InjectMock
    GitService gitService;

    @Inject
    CloudPassportLoader loader;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
                    FileUtils.copyDirectory(new File("src/test/resources/" + invocation.getArgument(0)), invocation.getArgument(1));
                    return null;
                }
        ).when(gitService).cloneRepository(anyString(), any());

    }


    @Test
    @TestConfigProperty(key = "colly.eis.cloud.passport.folder", value = "target/test-cloud-passport-folder")
    void load_cloud_passports_from_test_folder() {
        Project project1 = new Project("1", "project-1", ProjectType.PROJECT, "some-customer",
                List.of(new InstanceRepository("gitrepo_with_cloudpassports", "gitrepo_with_cloudpassports", "42")), List.of(), ClusterPlatform.K8S);
        Project project2 = new Project("2", "project-2", ProjectType.PROJECT, "some-customer",
                List.of(new InstanceRepository("gitrepo_with_unreachable_cluster", "gitrepo_with_unreachable_cluster", "43")), List.of(), ClusterPlatform.K8S);
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
                argocd:
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
        assertThat(result.argocd().argocdUrl(), equalTo("https://argo.example.com"));

    }

    @Test
    void testParseTokenFromCredsFile_validYaml(@TempDir Path tempDir) throws IOException {
        CloudData cloud = new CloudData(null, null, "tokenKey",
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
        CloudData cloud = new CloudData(null, null, "missingKey",
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
