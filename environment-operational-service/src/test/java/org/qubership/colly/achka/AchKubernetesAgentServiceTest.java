package org.qubership.colly.achka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.qubership.colly.db.data.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@QuarkusTest
class AchKubernetesAgentServiceTest {

    @Inject
    AchKubernetesAgentService service;

    @InjectMock
    AchKubernetesAgentClientFactory clientFactory;

    private AchKubernetesAgentClient setupMockClient(AchKubernetesAgentClient.AchkaResponse response) {
        AchKubernetesAgentClient client = mock(AchKubernetesAgentClient.class);
        when(clientFactory.create(anyString())).thenReturn(client);
        when(client.versions(anyList(), anyString())).thenReturn(response);
        return client;
    }

    @Test
    void shouldSkipNoneDeploymentSessionId() {
        var response = new AchKubernetesAgentClient.AchkaResponse(Map.of(
                "None", List.of(
                        new ApplicationsVersion(null, null, "1756355805", null)
                )
        ));

        setupMockClient(response);
        List<DeploymentOperation> result = service.getDeploymentOperations("cloud.example.com", List.of("ns1"));
        assertTrue(result.isEmpty());
    }


    @Test
    void shouldProcessValidSessionWithAllSuccess() {
        var response = new AchKubernetesAgentClient.AchkaResponse(Map.of(
                "session:123", List.of(
                        new ApplicationsVersion("sd-product-a", "SUCCESS", "1000000", "t1"),
                        new ApplicationsVersion("sd-product-a", "SUCCESS", "2000000", "t2"),
                        new ApplicationsVersion("sd-product-b", "SUCCESS", "3000000", "t3")
                )
        ));

        setupMockClient(response);
        List<DeploymentOperation> result = service.getDeploymentOperations("cloud.example.com", List.of("ns1"));

        assertEquals(1, result.size());
        DeploymentOperation op = result.getFirst();
        assertEquals(2, op.deploymentItems().size());
        op.deploymentItems().forEach(item -> {
            assertEquals(DeploymentStatus.SUCCESS, item.status());
            assertEquals(DeploymentItemType.PRODUCT, item.deploymentItemType());
            assertEquals(DeploymentMode.ROLLING_UPDATE, item.deploymentMode());
        });
    }

    @Test
    void shouldSetFailedStatusWhenAnyAppFailed() {
        var response = new AchKubernetesAgentClient.AchkaResponse(Map.of(
                "session:456", List.of(
                        new ApplicationsVersion("sd-product-a", "SUCCESS", "1000000", "t1"),
                        new ApplicationsVersion("sd-product-a", "FAILED", "2000000", "t2")
                )
        ));

        setupMockClient(response);
        List<DeploymentOperation> result = service.getDeploymentOperations("cloud.example.com", List.of("ns1"));

        assertEquals(1, result.size());
        assertEquals(1, result.getFirst().deploymentItems().size());
        DeploymentItem item = result.getFirst().deploymentItems().getFirst();
        assertEquals(DeploymentStatus.FAILED, item.status());
        assertEquals("sd-product-a", item.name());
    }

    @Test
    void shouldPickLatestDeployDateAsCompletedAt() {
        var response = new AchKubernetesAgentClient.AchkaResponse(Map.of(
                "session:789", List.of(
                        new ApplicationsVersion("sd-a", "SUCCESS", "1000000", "t1"),
                        new ApplicationsVersion("sd-b", "SUCCESS", "3000000", "t2"),
                        new ApplicationsVersion("sd-a", "SUCCESS", "2000000", "t3")
                )
        ));

        setupMockClient(response);
        List<DeploymentOperation> result = service.getDeploymentOperations("cloud.example.com", List.of("ns1"));

        assertEquals(1, result.size());
        assertEquals(Instant.ofEpochMilli(3000000L), result.getFirst().createdAt());
    }

    @Test
    void shouldProcessMultipleValidSessions() {
        Map<String, List<ApplicationsVersion>> sessions = new LinkedHashMap<>();
        sessions.put("session:1", List.of(
                new ApplicationsVersion("sd-a", "SUCCESS", "1000000", "t1")
        ));
        sessions.put("session:2", List.of(
                new ApplicationsVersion("sd-b", "FAILED", "2000000", "t2")
        ));
        var response = new AchKubernetesAgentClient.AchkaResponse(sessions);

        setupMockClient(response);
        List<DeploymentOperation> result = service.getDeploymentOperations("cloud.example.com", List.of("ns1"));
        assertEquals(2, result.size());
    }

    @Test
    void shouldSkipInvalidAndProcessValidSessions() {
        Map<String, List<ApplicationsVersion>> sessions = new LinkedHashMap<>();
        sessions.put("None", List.of(
                new ApplicationsVersion(null, null, "1000000", null)
        ));
        sessions.put("valid:session", List.of(
                new ApplicationsVersion("sd-a", "SUCCESS", "3000000", "t1")
        ));
        var response = new AchKubernetesAgentClient.AchkaResponse(sessions);

        setupMockClient(response);
        List<DeploymentOperation> result = service.getDeploymentOperations("cloud.example.com", List.of("ns1"));

        assertEquals(1, result.size());
        assertEquals("sd-a", result.getFirst().deploymentItems().getFirst().name());
    }

    @Test
    void shouldReturnEmptyListForEmptyResponse() {
        var response = new AchKubernetesAgentClient.AchkaResponse(Collections.emptyMap());

        setupMockClient(response);
        List<DeploymentOperation> result = service.getDeploymentOperations("cloud.example.com", List.of("ns1"));
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldPassCorrectParametersToClient() {
        var response = new AchKubernetesAgentClient.AchkaResponse(Collections.emptyMap());

        AchKubernetesAgentClient client = setupMockClient(response);
        List<String> namespaces = List.of("ns1", "ns2");

        service.getDeploymentOperations("cloud.example.com", namespaces);

        verify(clientFactory).create("cloud.example.com");
        verify(client).versions(namespaces, "deployment_session_id");
    }

    @Test
    void shouldSkipAllSessionsFromAchkaResponseJson() throws IOException {
        AchKubernetesAgentClient.AchkaResponse response = loadAchkaResponse("achka_response_empty.json");

        setupMockClient(response);
        List<DeploymentOperation> result = service.getDeploymentOperations("cloud.example.com", List.of("ns1"));

        // achka_response.json contains only "None" key which is invalid
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldProcessValidSessionsFromAchkaResponseValidJson() throws IOException {
        AchKubernetesAgentClient.AchkaResponse response = loadAchkaResponse("achka_response_valid.json");

        setupMockClient(response);
        List<DeploymentOperation> result = service.getDeploymentOperations("cloud.example.com", List.of("ns1"));

        // achka_response_valid.json contains:
        // - "None" (skipped)
        // - "some-session-id" with 2 sd: sd-product-alpha (SUCCESS), sd-product-beta (FAILED)
        // - "some-session-id-2" with 1 sd: sd-product-gamma (SUCCESS)
        assertEquals(2, result.size());
        assertThat(result, containsInAnyOrder(
                new DeploymentOperation(Instant.ofEpochMilli(1756600000L), List.of(
                        new DeploymentItem("sd-product-beta:42", DeploymentStatus.FAILED, DeploymentItemType.PRODUCT, DeploymentMode.ROLLING_UPDATE),
                        new DeploymentItem("sd-product-alpha:1", DeploymentStatus.SUCCESS, DeploymentItemType.PRODUCT, DeploymentMode.ROLLING_UPDATE)
                )),
                new DeploymentOperation(Instant.ofEpochMilli(1756700000L), List.of(
                        new DeploymentItem("sd-product-gamma:3", DeploymentStatus.SUCCESS, DeploymentItemType.PRODUCT, DeploymentMode.ROLLING_UPDATE)
                ))
        ));

    }

    private AchKubernetesAgentClient.AchkaResponse loadAchkaResponse(String filename) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
            return mapper.readValue(is, AchKubernetesAgentClient.AchkaResponse.class);
        }
    }
}
