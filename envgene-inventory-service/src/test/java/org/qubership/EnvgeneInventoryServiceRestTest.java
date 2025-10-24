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
                .when().get("/colly/envgene-inventory-service/environments")
                .then()
                .statusCode(401);
    }


    @Test
    @Disabled("Skip because auth was turned off for service")
    void load_clusters_without_auth() {
        given()
                .when().get("/colly/envgene-inventory-service/clusters")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test")
    void load_clusters() {
        given()
                .when().post("/colly/envgene-inventory-service/tick")
                .then()
                .statusCode(204);
        given()
                .when().get("/colly/envgene-inventory-service/clusters")
                .then()
                .statusCode(200)
                .body("name", contains("test-cluster", "unreachable-cluster"))
                .body("environments.flatten()", containsInAnyOrder(
                        allOf(
                                hasEntry("name", "env-test"),
                                hasEntry("owner", "test-owner"),
                                hasEntry("description", "some env for tests")
                        ),
                        allOf(
                                hasEntry("name", "env-metadata-test"),
                                hasEntry("owner", "owner from metadata"),
                                hasEntry("description", "description from metadata")
                        ),
                        allOf(
                                hasEntry("name", "env-1"),
                                hasEntry("description", "some env for tests")
                        )
                ));
    }


    @Test
    @TestSecurity(user = "test")
    void get_authStatus_for_regular_user() {
        given()
                .when().get("/colly/envgene-inventory-service/auth-status")
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
                .when().get("/colly/envgene-inventory-service/auth-status")
                .then()
                .statusCode(200)
                .body("username", equalTo("admin"))
                .body("isAdmin", equalTo(true))
                .body("authenticated", equalTo(true));
    }


    @Test
    void get_authStatus_without_auth() {
        given()
                .when().get("/colly/envgene-inventory-service/auth-status")
                .then()
                .statusCode(401)
                .body("authenticated", equalTo(false));
    }


    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void update_environment_with_auth() {
        given()
                .when().post("/colly/envgene-inventory-service/tick")
                .then()
                .statusCode(204);

        given()
                .contentType("application/json")
                .body("{\"name\":\"env-test\",\"owner\":\"new-owner\",\"description\":\"Updated description\",\"labels\":[\"test\",\"test2\"]}")
                .when().put("/colly/envgene-inventory-service/clusters/test-cluster/environments/env-test")
                .then()
                .statusCode(200)
                .body("name", equalTo("env-test"))
                .body("owner", equalTo("new-owner"))
                .body("description", equalTo("Updated description"))
                .body("labels", contains("test", "test2"));

        given()
                .when().get("/colly/envgene-inventory-service/clusters")
                .then()
                .statusCode(200)
                .body("environments.flatten()", hasItem(
                        allOf(
                                hasEntry("name", "env-test"),
                                hasEntry("owner", "new-owner"),
                                hasEntry("description", "Updated description")
                        )
                ))
                .body("environments.flatten().find { it.name == 'env-test' }.labels", contains("test", "test2"));
    }
}
