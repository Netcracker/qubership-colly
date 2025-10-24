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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
            Set.of(new CloudPassportEnvironment(
                            "env-test",
                            "some env for tests",
                            List.of(new CloudPassportNamespace("demo-k8s")),
                            List.of("test-owner"),
                            List.of()),
                    new CloudPassportEnvironment(
                            "env-metadata-test",
                            "description from metadata",
                            List.of(new CloudPassportNamespace("test-ns")),
                            List.of("owner from metadata"),
                            List.of("label1", "label2"))),
            URI.create("http://localhost:8428"),
            new GitInfo("gitrepo_with_cloudpassports", "target/test-cloud-passport-folder/1"));
    private static final CloudPassport UNREACHABLE_CLUSTER = new CloudPassport("unreachable-cluster",
            "1234567890",
            "https://some.unreachable.url:8443",
            Set.of(new CloudPassportEnvironment(
                    "env-1",
                    "some env for tests",
                    List.of(new CloudPassportNamespace("namespace-2"), new CloudPassportNamespace("namespace-1")),
                    List.of(),
                    List.of())),
            URI.create("http://vmsingle-k8s.victoria:8429"),
            new GitInfo("gitrepo_with_unreachable_cluster", "target/test-cloud-passport-folder/2")
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
    @TestConfigProperty(key = "colly.eis.env.instances.repo", value = "gitrepo_with_cloudpassports,gitrepo_with_unreachable_cluster")
    void load_cloud_passports_from_test_folder() {
        List<CloudPassport> result = loader.loadCloudPassports();
        assertThat(result, containsInAnyOrder(TEST_CLUSTER, UNREACHABLE_CLUSTER));

    }

    @Test
    @TestConfigProperty(key = "colly.eis.cloud.passport.folder", value = "/nonexistent/path")
    void load_cloud_passports_from_test_folder_with_empty_folder() {
        List<CloudPassport> result = loader.loadCloudPassports();
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
                cse:
                  MONITORING_NAMESPACE: "monitoring"
                  MONITORING_TYPE: "VictoriaDB"
                  MONITORING_EXT_MONITORING_QUERY_URL: "http://monitoring.example.com"
                """;
        Path file = tempDir.resolve("data.yml");
        Files.writeString(file, yaml);

        CloudPassportData result = loader.parseCloudPassportDataFile(file);
        assertNotNull(result);
        assertThat(result.getCloud(),
                allOf(
                        hasProperty("cloudApiHost", equalTo("api.example.com")),
                        hasProperty("cloudApiPort", equalTo("443")),
                        hasProperty("cloudProtocol", equalTo("https"))));
        assertThat(result.getCse(),
                allOf(
                        hasProperty("monitoringNamespace", equalTo("monitoring")),
                        hasProperty("monitoringType", equalTo("VictoriaDB")),
                        hasProperty("monitoringExtMonitoringQueryUrl", equalTo("http://monitoring.example.com"))));
    }

    @Test
    void testParseTokenFromCredsFile_validYaml(@TempDir Path tempDir) throws IOException {
        CloudData cloud = new CloudData();
        cloud.setCloudDeployToken("tokenKey");

        CloudPassportData passportData = new CloudPassportData();
        passportData.setCloud(cloud);

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
        CloudData cloud = new CloudData();
        cloud.setCloudDeployToken("missingKey");

        CloudPassportData passportData = new CloudPassportData();
        passportData.setCloud(cloud);

        String yaml = """
                anotherKey:
                  secret: "data"
                """;
        Path file = tempDir.resolve("creds.yml");
        Files.writeString(file, yaml);

        assertThrows(RuntimeException.class, () -> loader.parseTokenFromCredsFile(file, passportData));
    }
}
