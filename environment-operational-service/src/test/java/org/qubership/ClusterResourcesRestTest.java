package org.qubership;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.qubership.colly.EnvgeneInventoryServiceRest;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;
import org.qubership.colly.cloudpassport.ClusterInfo;

import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
// @TestTransaction - removed for Redis
class ClusterResourcesRestTest {

    @InjectMock
    @RestClient
    EnvgeneInventoryServiceRest envgeneInventoryServiceRest;

    @BeforeEach
    void setUp() {
        Mockito.when(envgeneInventoryServiceRest.getClusterInfos()).thenReturn(List.of(
                new ClusterInfo("1", "test-cluster", "cloud-deploy-sa-token", "https://1E4A399FCB54F505BBA05320EADF0DB3.gr7.eu-west-1.eks.amazonaws.com:443",
                        Set.of(new CloudPassportEnvironment("42", "env-test", "", List.of())), "http://localhost:8428"),
                new ClusterInfo("2", "unreachable-cluster", "cloud-deploy-sa-token", "https://some.unreachable.url:8443",
                        Set.of(new CloudPassportEnvironment("43", "env-1", "", List.of())), "http://vmsingle-k8s.victoria:8429")));

    }

    @Test
    void load_environments_without_auth() {
        given()
                .when().get("/colly/v2/operational-service/environments")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test")
    void load_environments() {
        given()
                .when().post("/colly/v2/operational-service/manual-sync")
                .then()
                .statusCode(204);
        given()
                .when().get("/colly/v2/operational-service/environments")
                .then()
                .statusCode(200)
                .body("name", contains("env-test", "env-1"));
    }

    @Test
    void load_metadata_without_auth() {
        given()
                .when().get("/colly/v2/operational-service/metadata")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test")
    void load_metadata() {
        given()
                .when().get("/colly/v2/operational-service/metadata")
                .then()
                .statusCode(200)
                .body("monitoringColumns", contains("Failed Deployments", "Running Pods"));
    }

    @Test
    void load_clusters_without_auth() {
        given()
                .when().get("/colly/v2/operational-service/clusters")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test")
    void load_clusters() {
        given()
                .when().post("/colly/v2/operational-service/manual-sync")
                .then()
                .statusCode(204);
        given()
                .when().get("/colly/v2/operational-service/clusters")
                .then()
                .statusCode(200)
                .body("name", contains("test-cluster", "unreachable-cluster"));
    }


    @Test
    @TestSecurity(user = "test")
    void get_authStatus_for_regular_user() {
        given()
                .when().get("/colly/v2/operational-service/auth-status")
                .then()
                .statusCode(200)
                .body("username", equalTo("test"))
                .body("isAdmin", equalTo(false))
                .body("authenticated", equalTo(true));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void get_authStatus_for_admin() {
        given()
                .when().get("/colly/v2/operational-service/auth-status")
                .then()
                .statusCode(200)
                .body("username", equalTo("admin"))
                .body("isAdmin", equalTo(true))
                .body("authenticated", equalTo(true));
    }


    @Test
    void get_authStatus_without_auth() {
        given()
                .when().get("/colly/v2/operational-service/auth-status")
                .then()
                .statusCode(401)
                .body("authenticated", equalTo(false));
    }


}
