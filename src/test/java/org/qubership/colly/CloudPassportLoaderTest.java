package org.qubership.colly;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.qubership.colly.cloudpassport.CloudPassport;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;
import org.qubership.colly.cloudpassport.CloudPassportNamespace;
import org.qubership.colly.cloudpassport.envgen.CloudData;
import org.qubership.colly.cloudpassport.envgen.CloudPassportData;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@QuarkusTest
class CloudPassportLoaderTest {

    private static final CloudPassport TEST_CLUSTER_CLOUD_PASSPORT = new CloudPassport("test-cluster",
            "eyJhbGciOiJSUzI1NiIsImtpZCI6IkhIQjJJMDU0azFDbERMcXBEYVk0ckdLbGhMQnlSVnB4aWhaYzBia2tpdncifQ.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZW1vLWs4cyIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VjcmV0Lm5hbWUiOiJkZW1vLWs4cy1zZWNyZXQiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiZGVtby1rOHMiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC51aWQiOiIyMzk4OGZhYi05OWU5LTQ1ODYtYWIxNi0wZWI1NmExY2Q3YmEiLCJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQ6ZGVtby1rOHM6ZGVtby1rOHMifQ.TOfvRb-JO7GON_O8Zmb4gfLjnbuwOn8cMija4qW1z4Et_4BlipT_vf4Bv5sXnw36KjdVD-sWkyIUza10_XQdpXFqGj0xTinS-SEXuqQ-r2d5tVTEBQ_H8ALDqN_9oA5r0TcH18SijuxgVfMDSEBXdfjzFTu2rxyK6cFbcYKZVmY3W9JD9Tpz3qB_FLa8MAbuDnCg5r4feohjpmiA7lDpRWwjjMEpByj-wGd3u3l-vBk6QW5Aw_iEqiG-AcbzM2Lx874Inlz8M60jLpbTdw58KQjNOlkUpmzzrHAcyBizeBCNl-7DCduY0sqE3ZTNn7sV3mNaec0TGWrSUhPIHTrHcQ",
            "https://1E4A399FCB54F505BBA05320EADF0DB3.gr7.eu-west-1.eks.amazonaws.com:443",
            List.of(new CloudPassportEnvironment(
                    "env-test",
                    "some env for tests",
                    List.of(new CloudPassportNamespace("demo-k8s")))),
            URI.create("http://localhost:8428"));
    @Inject
    CloudPassportLoader loader;

    @InjectMock
    GitService gitService;

    @BeforeEach
    void setUp() {
        doAnswer(invocationOnMock -> null).when(gitService).cloneRepository(any(), any());
    }


    @Test
    void load_cloud_passports_from_test_folder() {
        List<CloudPassport> result = loader.loadCloudPassports();
        assertThat(result, hasItem(TEST_CLUSTER_CLOUD_PASSPORT));
    }


    @Test
    void test_read_cloud_passport_data_file(@TempDir Path tempDir) throws IOException {
        String yaml = """
                cloud:
                  CLOUD_API_HOST: "api.example.com"
                  CLOUD_API_PORT: 443
                  CLOUD_PROTOCOL: "https"
                  CLOUD_DEPLOY_TOKEN: "tokenKey"
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
