package org.qubership;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.qubership.colly.EnvgeneInventoryServiceRest;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;
import org.qubership.colly.cloudpassport.CloudPassportNamespace;
import org.qubership.colly.cloudpassport.ClusterInfo;

import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.Matchers.*;

@QuarkusTest
// @TestTransaction - removed for Redis
class ClusterResourcesRestTest {

    @InjectMock
    @RestClient
    EnvgeneInventoryServiceRest envgeneInventoryServiceRest;

    @Inject
    RedisDataSource redisDataSource;

    @BeforeEach
    void setUp() {
        redisDataSource.flushall();

        Mockito.when(envgeneInventoryServiceRest.getClusterInfos()).thenReturn(List.of(
                new ClusterInfo("1", "test-cluster", "cloud-deploy-sa-token", "https://1E4A399FCB54F505BBA05320EADF0DB3.gr7.eu-west-1.eks.amazonaws.com:443",
                        Set.of(new CloudPassportEnvironment("42", "env-test", "some description", List.of(new CloudPassportNamespace("422", "namespace-1")))), "http://localhost:8428"),
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
    @TestSecurity(user = "test")
    void manual_sync_for_particular_cluster() {
        given()
                .when().post("/colly/v2/operational-service/manual-sync?clusterId=1")
                .then()
                .statusCode(204);
        given()
                .when().get("/colly/v2/operational-service/environments")
                .then()
                .statusCode(200)
                .body("name", contains("env-test"));
    }

    @Test
    @TestSecurity(user = "test")
    void manual_sync_for_nonexisting_cluster() {
        given()
                .when().post("/colly/v2/operational-service/manual-sync?clusterId=non-existent-cluster")
                .then()
                .statusCode(404);
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

    @Test
    @TestSecurity(user = "test")
    void get_environment_by_id() {
        given()
                .when().post("/colly/v2/operational-service/manual-sync")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/operational-service/environments/42")
                .then()
                .statusCode(200)
                .body("id", equalTo("42"))
                .body("name", equalTo("env-test"))
                .body("namespaces", contains(
                        allOf(
                                hasEntry("id", "422"),
                                hasEntry("name", "namespace-1")
                        )
                ))
                .body("cluster.id", equalTo("1"))
                .body("cluster.name", equalTo("test-cluster"))
                .body("cluster.lastSuccessfulSyncAt", nullValue());

    }

    @Test
    @TestSecurity(user = "test")
    void get_environment_by_id_not_found() {
        given()
                .when().post("/colly/v2/operational-service/manual-sync")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/operational-service/environments/non-existent-id")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "test")
    void get_cluster_by_id() {
        given()
                .when().post("/colly/v2/operational-service/manual-sync")
                .then()
                .statusCode(204);

        Response response = given()
                .when().get("/colly/v2/operational-service/clusters")
                .then()
                .statusCode(200)
                .extract().response();

        String clusterId = response.jsonPath().getString("[0].id");
        String clusterName = response.jsonPath().getString("[0].name");

        given()
                .when().get("/colly/v2/operational-service/clusters/" + clusterId)
                .then()
                .statusCode(200)
                .body("id", equalTo(clusterId))
                .body("name", equalTo(clusterName));
    }

    @Test
    @TestSecurity(user = "test")
    void get_cluster_by_id_not_found() {
        given()
                .when().post("/colly/v2/operational-service/manual-sync")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/operational-service/clusters/non-existent-cluster-id")
                .then()
                .statusCode(404);
    }

}
