package org.qubership;

import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.colly.GitService;
import org.qubership.colly.db.EnvironmentRepository;
import org.qubership.colly.db.data.Environment;

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

    @Inject
    EnvironmentRepository environmentRepository;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
                    FileUtils.copyDirectory(new File("src/test/resources/" + invocation.getArgument(0)), invocation.getArgument(1));
                    return null;
                }
        ).when(gitService).cloneRepository(anyString(), any());
    }

    @Test
    void load_environments_without_auth() {
        given()
                .when().get("/colly/v2/inventory-service/environments")
                .then()
                .statusCode(401);
    }


    @Test
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
                                hasEntry("role", null),
                                hasEntry("region", null)
                        ),
                        allOf(
                                hasEntry("name", "env-metadata-test"),
                                hasEntry("description", "description from metadata"),
                                hasEntry("status", "IN_USE"),
                                hasEntry("expirationDate", "2025-12-31"),
                                hasEntry("type", "DESIGN_TIME"),
                                hasEntry("role", "QA"),
                                hasEntry("region", "cm")
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
        Environment environment = environmentRepository.listAll().stream().filter(e -> e.getName().equals("env-test")).findFirst().orElseThrow();
        given()
                .contentType("application/json")
                .body("{\"owners\":[\"new-owner\"],\"description\":\"Updated description\",\"labels\":[\"test\",\"test2\"]}")
                .when().patch("/colly/v2/inventory-service/environments/" + environment.getId())
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

    @Test
    void update_environment_without_auth() {
        // Setup: sync to get some environments
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(401); // Even sync requires auth

        // Try to update without authentication
        given()
                .contentType("application/json")
                .body("{\"description\":\"Should not work\"}")
                .when().patch("/colly/v2/inventory-service/environments/some-id")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test")
    void update_environment_without_admin_role() {
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        Environment environment = environmentRepository.listAll().stream()
                .filter(e -> e.getName().equals("env-test"))
                .findFirst()
                .orElseThrow();

        given()
                .contentType("application/json")
                .body("{\"description\":\"Should not work\"}")
                .when().patch("/colly/v2/inventory-service/environments/" + environment.getId())
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void update_environment_with_empty_fields() {
        // Setup: sync to get env-metadata-test which has expirationDate = "2025-12-31"
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        Environment environment = environmentRepository.listAll().stream()
                .filter(e -> e.getName().equals("env-metadata-test"))
                .findFirst()
                .orElseThrow();

        // Verify initial state
        given()
                .when().get("/colly/v2/inventory-service/environments")
                .then()
                .statusCode(200)
                .body("find { it.name == 'env-metadata-test' }.description", equalTo("description from metadata"))
                .body("find { it.name == 'env-metadata-test' }.labels", contains("label1", "label2"))
                .body("find { it.name == 'env-metadata-test' }.teams", contains("team-from-metadata"))
                .body("find { it.name == 'env-metadata-test' }.status", equalTo("IN_USE"))
                .body("find { it.name == 'env-metadata-test' }.type", equalTo("DESIGN_TIME"))
                .body("find { it.name == 'env-metadata-test' }.role", equalTo("QA"))
                .body("find { it.name == 'env-metadata-test' }.region", equalTo("cm"))
                .body("find { it.name == 'env-metadata-test' }.expirationDate", equalTo("2025-12-31"))
                .body("find { it.name == 'env-metadata-test' }.owners", contains("owner from metadata"));


        // Also test clearing owners, expiration date with empty values
        given()
                .contentType("application/json")
                .body("{\"description\":\"\"," +
                        "\"labels\":[], " +
                        "\"teams\": []," +
                        "\"status\": null," +
                        "\"type\": null," +
                        "\"role\": \"\"," +
                        "\"region\": \"\"," +
                        "\"owners\": [], " +
                        "\"expirationDate\": \"\"}")
                .when().patch("/colly/v2/inventory-service/environments/" + environment.getId())
                .then()
                .statusCode(200);

        given()
                .when().get("/colly/v2/inventory-service/environments")
                .then()
                .statusCode(200)
                .body("find { it.name == 'env-metadata-test' }.description", equalTo(""))
                .body("find { it.name == 'env-metadata-test' }.labels", emptyIterable())
                .body("find { it.name == 'env-metadata-test' }.teams", emptyIterable())
                .body("find { it.name == 'env-metadata-test' }.status", equalTo("IN_USE"))
                .body("find { it.name == 'env-metadata-test' }.type", equalTo("DESIGN_TIME"))
                .body("find { it.name == 'env-metadata-test' }.role", equalTo(""))
                .body("find { it.name == 'env-metadata-test' }.region", equalTo(""))
                .body("find { it.name == 'env-metadata-test' }.owners", emptyIterable())
                .body("find { it.name == 'env-metadata-test' }.expirationDate", nullValue());
    }
}
