package org.qubership.colly.uiservice;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.colly.uiservice.client.InventoryServiceClient;
import org.qubership.colly.uiservice.client.OperationalServiceClient;
import org.qubership.colly.uiservice.dto.EnvironmentStatus;
import org.qubership.colly.uiservice.dto.EnvironmentType;
import org.qubership.colly.uiservice.dto.inventory.InventoryClusterDto;
import org.qubership.colly.uiservice.dto.inventory.InventoryClusterViewDto;
import org.qubership.colly.uiservice.dto.inventory.InventoryEnvironmentDto;
import org.qubership.colly.uiservice.dto.inventory.InventoryNamespaceDto;
import org.qubership.colly.uiservice.dto.operational.OperationalClusterDto;
import org.qubership.colly.uiservice.dto.operational.OperationalEnvironmentDto;
import org.qubership.colly.uiservice.dto.operational.OperationalNamespaceDto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

@QuarkusTest
class UIApiResourceTest {

    @InjectMock
    @RestClient
    InventoryServiceClient inventoryServiceClient;
    @InjectMock
    @RestClient
    OperationalServiceClient operationalServiceClient;

    @BeforeEach
    void setUp() {
        // Mock inventory service responses
        when(inventoryServiceClient.getEnvironments()).thenReturn(List.of(
                new InventoryEnvironmentDto(
                        "env-1",
                        "test-environment",
                        "Test Environment",
                        List.of(new InventoryNamespaceDto("ns-1", "namespace-1")),
                        new InventoryClusterViewDto("cluster-1", "test-cluster"),
                        List.of("owner-1"),
                        List.of("label-1"),
                        List.of("team-1"),
                        EnvironmentStatus.FREE,
                        null,
                        EnvironmentType.ENVIRONMENT,
                        null,
                        "cm"
                )
        ));

        when(inventoryServiceClient.getClusters()).thenReturn(List.of(
                new InventoryClusterDto("cluster-1", "test-cluster",
                        "https://dashboard.example.com",
                        "https://dbaas.example.com",
                        "https://deployer.example.com")
        ));

        // Mock operational service responses
        when(operationalServiceClient.getEnvironments()).thenReturn(List.of(
                new OperationalEnvironmentDto(
                        "env-1",
                        "test-environment",
                        List.of(new OperationalNamespaceDto("ns-1", "namespace-1", true)),
                        new OperationalClusterDto("cluster-1", "test-cluster", true),
                        "1.0.0",
                        Instant.now(),
                        Map.of("cpu", "50%", "memory", "2Gi")
                )
        ));

        when(operationalServiceClient.getClusters()).thenReturn(List.of(
                new OperationalClusterDto("cluster-1", "test-cluster", true)
        ));

        when(operationalServiceClient.getMetadata()).thenReturn(
                Map.of("version", "1.0.0", "status", "running")
        );
    }

    // ========== Auth Status Tests ==========

    @Test
    void getAuthStatus_without_auth() {
        given()
                .when().get("/colly/v2/ui-service/auth-status")
                .then()
                .statusCode(401)
                .body("authenticated", equalTo(false));
    }

    @Test
    @TestSecurity(user = "testuser")
    void getAuthStatus_with_regular_user() {
        given()
                .when().get("/colly/v2/ui-service/auth-status")
                .then()
                .statusCode(200)
                .body("authenticated", equalTo(true))
                .body("username", equalTo("testuser"))
                .body("isAdmin", equalTo(false));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void getAuthStatus_with_admin() {
        given()
                .when().get("/colly/v2/ui-service/auth-status")
                .then()
                .statusCode(200)
                .body("authenticated", equalTo(true))
                .body("username", equalTo("admin"))
                .body("isAdmin", equalTo(true));
    }


    @Test
    @TestSecurity(user = "testuser")
    void getEnvironments_success() {
        given()
                .when().get("/colly/v2/ui-service/environments")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo("env-1"))
                .body("[0].name", equalTo("test-environment"))
                .body("[0].description", equalTo("Test Environment"))
                .body("[0].deploymentVersion", equalTo("1.0.0"))
                .body("[0].namespaces.size()", equalTo(1))
                .body("[0].namespaces[0].name", equalTo("namespace-1"))
                .body("[0].namespaces[0].existsInK8s", equalTo(true));
    }


    @Test
    @TestSecurity(user = "test")
    void getClusters_success() {
        given()
                .when().get("/colly/v2/ui-service/clusters")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo("cluster-1"))
                .body("[0].name", equalTo("test-cluster"))
                .body("[0].synced", equalTo(true));
    }

    @Test
    @TestSecurity(user = "test")
    void getMetadata_success() {
        given()
                .when().get("/colly/v2/ui-service/metadata")
                .then()
                .statusCode(200)
                .body("version", equalTo("1.0.0"))
                .body("status", equalTo("running"));
    }

    @Test
    @TestSecurity(user = "test")
    void health_check() {
        given()
                .when().get("/colly/v2/ui-service/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("service", equalTo("ui-service"));
    }
}
