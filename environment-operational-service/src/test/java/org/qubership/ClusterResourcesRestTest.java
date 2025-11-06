package org.qubership;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.qubership.colly.EnvgeneInventoryServiceRest;
import org.qubership.colly.cloudpassport.CloudPassport;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.repository.EnvironmentRepository;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
// @TestTransaction - removed for Redis
class ClusterResourcesRestTest {
    @Inject
    EnvironmentRepository environmentRepository;

    @InjectMock
    @RestClient
    EnvgeneInventoryServiceRest envgeneInventoryServiceRest;

    @BeforeEach
    void setUp() {
        Mockito.when(envgeneInventoryServiceRest.getCloudPassports()).thenReturn(List.of(
                new CloudPassport("test-cluster", "cloud-deploy-sa-token", "https://1E4A399FCB54F505BBA05320EADF0DB3.gr7.eu-west-1.eks.amazonaws.com:443",
                        Set.of(new CloudPassportEnvironment("42","env-test", "", List.of())), URI.create("http://localhost:8428")),
                new CloudPassport("unreachable-cluster", "cloud-deploy-sa-token", "https://some.unreachable.url:8443",
                        Set.of(new CloudPassportEnvironment("43","env-1", "", List.of())), URI.create("http://vmsingle-k8s.victoria:8429"))));

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
                .when().post("/colly/v2/operational-service/tick")
                .then()
                .statusCode(204);
        given()
                .when().get("/colly/v2/operational-service/environments")
                .then()
                .statusCode(200)
                .body("name", contains("env-test", "env-1"));
    }


    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void save_cluster_with_auth() {
        given()
                .when().post("/colly/v2/operational-service/tick")
                .then()
                .statusCode(204);

        given()
                .formParam("description", "test-cluster-description")
                .when().post("/colly/v2/operational-service/clusters/test-cluster")
                .then()
                .statusCode(204);
        given()
                .when().get("/colly/v2/operational-service/clusters")
                .then()
                .statusCode(200)
                .body("description", hasItem("test-cluster-description"));
    }

    @Test
    @TestSecurity(user = "test")
    void save_cluster_without_admin_role() {
        given()
                .formParam("description", "test-cluster-description")
                .when().post("/colly/v2/operational-service/clusters/test-cluster")
                .then()
                .statusCode(403);
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
                .when().post("/colly/v2/operational-service/tick")
                .then()
                .statusCode(204);
        given()
                .when().get("/colly/v2/operational-service/clusters")
                .then()
                .statusCode(200)
                .body("name", contains("test-cluster", "unreachable-cluster"));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void try_to_save_non_existing_cluster() {
        given()
                .formParam("description", "test-cluster-description")
                .when().post("/colly/v2/operational-service/clusters/non-existing-cluster")
                .then()
                .statusCode(400);
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
    void unable_to_delete_environment_without_admin_role() {
        given()
                .when().delete("/colly/v2/operational-service/environments/1")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    @Disabled("todo: need to improve stub for inventory service")
    void delete_environment_with_auth() {
        given()
                .when().post("/colly/v2/operational-service/tick")
                .then()
                .statusCode(204);
        given()
                .when().get("/colly/v2/operational-service/environments")
                .then()
                .statusCode(200)
                .body("name", hasItem("env-test"));
        List<Environment> envs = environmentRepository.findByName("env-test");
        Environment env = envs.stream().filter(e -> "test-cluster".equals(e.getClusterId())).findFirst().orElse(null);

        given()
                .when().delete("/colly/v2/operational-service/environments/" + env.getId())
                .then()
                .statusCode(204);
        given()
                .when().get("/colly/v2/operational-service/environments")
                .then()
                .statusCode(200)
                .body("name", not(hasItem("env-test")));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void delete_environment_with_non_existing_id() {
        given()
                .when().delete("/colly/v2/operational-service/environments/9999") // Non-existing environment ID
                .then()
                .statusCode(400);
    }

}
