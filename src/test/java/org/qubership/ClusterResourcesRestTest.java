package org.qubership;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestTransaction
class ClusterResourcesRestTest {

    @Test
    void load_environments_without_auth() {
        given()
                .when().get("/colly/environments")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test")
    void load_environments() {
        given()
                .when().post("/colly/tick")
                .then()
                .statusCode(204);
        given()
                .when().get("/colly/environments")
                .then()
                .statusCode(200)
                .body("name", contains("env-test", "env-1"));
    }


    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void save_environment_with_auth() {
        given()
                .when().post("/colly/tick")
                .then()
                .statusCode(204);

        given()
                .formParam("owner", "test-owner")
                .formParam("description", "test-description")
                .formParam("status", "active")
                .formParam("labels", "label1,label2")
                .formParam("type", "development")
                .formParam("team", "test-team")
                .formParam("expirationDate", "2024-12-31")
                .when().post("/colly/environments/1")
                .then()
                .statusCode(204);
    }

    @Test
    @TestSecurity(user = "test")
    void save_environment_without_admin_role() {
        given()
                .formParam("name", "test-env")
                .formParam("owner", "test-owner")
                .formParam("description", "test-description")
                .formParam("status", "active")
                .formParam("labels", "label1,label2")
                .formParam("type", "development")
                .formParam("team", "test-team")
                .when().post("/colly/environments/1")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void save_cluster_with_auth() {
        given()
                .when().post("/colly/tick")
                .then()
                .statusCode(204);

        given()
                .formParam("description", "test-cluster-description")
                .when().post("/colly/clusters/test-cluster")
                .then()
                .statusCode(204);
        given()
                .when().get("/colly/clusters")
                .then()
                .statusCode(200)
                .body("description", hasItem("test-cluster-description"));
    }

    @Test
    @TestSecurity(user = "test")
    void save_cluster_without_admin_role() {
        given()
                .formParam("description", "test-cluster-description")
                .when().post("/colly/clusters/test-cluster")
                .then()
                .statusCode(403);
    }

    @Test
    void load_metadata_without_auth() {
        given()
                .when().get("/colly/metadata")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test")
    void load_metadata() {
        given()
                .when().get("/colly/metadata")
                .then()
                .statusCode(200)
                .body("monitoringColumns", contains("Failed Deployments", "Running Pods"));
    }

    @Test
    void load_clusters_without_auth() {
        given()
                .when().get("/colly/clusters")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test")
    void load_clusters() {
        given()
                .when().post("/colly/tick")
                .then()
                .statusCode(204);
        given()
                .when().get("/colly/clusters")
                .then()
                .statusCode(200)
                .body("name", contains("test-cluster", "unreachable-cluster"));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void try_to_save_non_existing_environment() {
        given()
                .formParam("owner", "test-owner")
                .formParam("description", "test-description")
                .when().post("/colly/environments/42") // Non-existing environment ID
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void try_to_save_non_existing_cluster() {
        given()
                .formParam("description", "test-cluster-description")
                .when().post("/colly/clusters/non-existing-cluster")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test")
    void get_authStatus_for_regular_user() {
        given()
                .when().get("/colly/auth-status")
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
                .when().get("/colly/auth-status")
                .then()
                .statusCode(200)
                .body("username", equalTo("admin"))
                .body("isAdmin", equalTo(true))
                .body("authenticated", equalTo(true));
    }


    @Test
    void get_authStatus_without_auth() {
        given()
                .when().get("/colly/auth-status")
                .then()
                .statusCode(401)
                .body("authenticated", equalTo(false));
    }

}
