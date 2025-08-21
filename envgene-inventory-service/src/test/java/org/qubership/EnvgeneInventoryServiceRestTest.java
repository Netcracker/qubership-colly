package org.qubership;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.qubership.colly.db.EnvironmentRepository;
import org.qubership.colly.db.data.Environment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestTransaction
class EnvgeneInventoryServiceRestTest {

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
