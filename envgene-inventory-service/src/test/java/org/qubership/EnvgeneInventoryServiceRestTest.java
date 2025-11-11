package org.qubership;

import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.qubership.colly.GitService;

import java.io.File;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@QuarkusTest
@TestTransaction
class EnvgeneInventoryServiceRestTest {

    @InjectMock
    GitService gitService;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
                    FileUtils.copyDirectory(new File("src/test/resources/" + invocation.getArgument(0)), invocation.getArgument(1));
                    return null;
                }
        ).when(gitService).cloneRepository(anyString(), any());
    }

    @Test
    @Disabled("Skip because auth was turned off for service")
    void load_environments_without_auth() {
        given()
                .when().get("/colly/v2/inventory-service/environments")
                .then()
                .statusCode(401);
    }


    @Test
    @Disabled("Skip because auth was turned off for service")
    void load_clusters_without_auth() {
        given()
                .when().get("/colly/v2/inventory-service/internal/cluster-infos")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test")
    void load_clusters() {
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);
        given()
                .when().get("/colly/v2/inventory-service/internal/cluster-infos")
                .then()
                .statusCode(200)
                .body("name", contains("test-cluster", "unreachable-cluster"))
                .body("environments.flatten()", containsInAnyOrder(
                        hasEntry("name", "env-test"),
                        hasEntry("name", "env-metadata-test"),
                        hasEntry("name", "env-1")
                ));
    }

    @Test
    @TestSecurity(user = "test")
    void load_environments() {
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);
        given()
                .when().get("/colly/v2/inventory-service/environments")
                .then()
                .statusCode(200)
                .body("id", everyItem(notNullValue()))
                .body("namespaces", everyItem(notNullValue()))
                .body(".", containsInAnyOrder(
                        allOf(
                                hasEntry("name", "env-test"),
                                hasEntry("description", "some env for tests"),
                                hasEntry("status", "FREE"),
                                hasEntry("expirationDate", null),
                                hasEntry("type", "ENVIRONMENT"),
                                hasEntry("role", null)
                        ),
                        allOf(
                                hasEntry("name", "env-metadata-test"),
                                hasEntry("description", "description from metadata"),
                                hasEntry("status", "IN_USE"),
                                hasEntry("expirationDate", "2025-12-31"),
                                hasEntry("type", "DESIGN_TIME"),
                                hasEntry("role", "QA")
                        ),
                        allOf(
                                hasEntry("name", "env-1"),
                                hasEntry("description", "some env for tests")
                        )
                ))
                .body("find { it.name == 'env-metadata-test' }.teams", contains("team-from-metadata"))
                .body("find { it.name == 'env-metadata-test' }.owners", contains("owner from metadata"));
    }


    @Test
    @TestSecurity(user = "test")
    void get_authStatus_for_regular_user() {
        given()
                .when().get("/colly/v2/inventory-service/auth-status")
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
                .when().get("/colly/v2/inventory-service/auth-status")
                .then()
                .statusCode(200)
                .body("username", equalTo("admin"))
                .body("isAdmin", equalTo(true))
                .body("authenticated", equalTo(true));
    }


    @Test
    void get_authStatus_without_auth() {
        given()
                .when().get("/colly/v2/inventory-service/auth-status")
                .then()
                .statusCode(401)
                .body("authenticated", equalTo(false));
    }


    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void update_environment_with_auth() {
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        given()
                .contentType("application/json")
                .body("{\"id\":\"42\", \"name\":\"env-test\",\"owners\":[\"new-owner\"],\"description\":\"Updated description\",\"labels\":[\"test\",\"test2\"]}")
                .when().put("/colly/v2/inventory-service/environments/42")
                .then()
                .statusCode(200)
                .body("name", equalTo("env-test"))
                .body("owners", contains("new-owner"))
                .body("description", equalTo("Updated description"))
                .body("labels", contains("test", "test2"));

        given()
                .when().get("/colly/v2/inventory-service/environments")
                .then()
                .statusCode(200)
                .body("flatten()", hasItem(
                        allOf(
                                hasEntry("name", "env-test"),
                                hasEntry("description", "Updated description")
                        )
                ))
                .body("flatten().find { it.name == 'env-test' }.labels", contains("test", "test2"))
                .body("flatten().find { it.name == 'env-test' }.owners", contains("new-owner"));
    }
}
