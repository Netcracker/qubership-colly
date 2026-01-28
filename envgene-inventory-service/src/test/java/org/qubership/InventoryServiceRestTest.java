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
import org.qubership.colly.db.ClusterRepository;
import org.qubership.colly.db.EnvironmentRepository;
import org.qubership.colly.db.data.Cluster;
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
class InventoryServiceRestTest {

    @InjectMock
    GitService gitService;

    @Inject
    EnvironmentRepository environmentRepository;

    @Inject
    ClusterRepository clusterRepository;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            FileUtils.copyDirectory(new File("src/test/resources/" + invocation.getArgument(0)), invocation.getArgument(3));
                    return null;
                }
        ).when(gitService).cloneRepository(anyString(), any(), any(), any());
    }

    @Test
    void get_environments_without_auth() {
        given()
                .when().get("/colly/v2/inventory-service/environments")
                .then()
                .statusCode(401);
    }


    @Test
    void get_clusters_internal_infos_without_auth() {
        given()
                .when().get("/colly/v2/inventory-service/internal/cluster-infos")
                .then()
                .statusCode(401);
    }

    @Test
    void get_clusters_without_auth() {
        given()
                .when().get("/colly/v2/inventory-service/clusters")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test")
    void get_cluster_internal_infos() {
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
    void get_clusters() {
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);
        given()
                .when().get("/colly/v2/inventory-service/clusters")
                .then()
                .statusCode(200)
                .body("id", everyItem(notNullValue()))
                .body("name", containsInAnyOrder("test-cluster", "unreachable-cluster"))
                .body("find { it.name == 'test-cluster' }.environments.name",
                        containsInAnyOrder("env-test", "env-metadata-test"))
                .body("find { it.name == 'test-cluster' }.dashboardUrl",
                        equalTo("https://dashboard.example.com"))
                .body("find { it.name == 'test-cluster' }.dbaasUrl",
                        equalTo("https://dbaas.example.com"))
                .body("find { it.name == 'test-cluster' }.deployerUrl",
                        equalTo("https://deployer.example.com"))
                .body("find { it.name == 'test-cluster' }.argoUrl",
                        equalTo("https://argo.example.com"))
                .body(".", hasSize(2));
    }

    @Test
    @TestSecurity(user = "test")
    void get_cluster_by_id() {
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        Cluster cluster = clusterRepository.listAll().stream()
                .filter(c -> c.getName().equals("test-cluster"))
                .findFirst()
                .orElseThrow();

        given()
                .when().get("/colly/v2/inventory-service/clusters/" + cluster.getId())
                .then()
                .statusCode(200)
                .body("id", equalTo(cluster.getId()))
                .body("name", equalTo("test-cluster"))
                .body("dashboardUrl", equalTo("https://dashboard.example.com"))
                .body("dbaasUrl", equalTo("https://dbaas.example.com"))
                .body("deployerUrl", equalTo("https://deployer.example.com"))
                .body("argoUrl", equalTo("https://argo.example.com"))
                .body("environments.name", containsInAnyOrder("env-test", "env-metadata-test"))
                .body("environments", hasSize(2));
    }

    @Test
    @TestSecurity(user = "test")
    void get_cluster_by_id_not_found() {
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/clusters/non-existent-cluster-id")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "test")
    void get_clusters_by_project_id() {
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);
        given()
                .when().get("/colly/v2/inventory-service/clusters?projectId=solar_earth")
                .then()
                .statusCode(200)
                .body("name", containsInAnyOrder("test-cluster"));
        given()
                .when().get("/colly/v2/inventory-service/clusters?projectId=solar_saturn")
                .then()
                .statusCode(200)
                .body("name", containsInAnyOrder("unreachable-cluster"));
    }

    @Test
    @TestSecurity(user = "test")
    void get_environments() {
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
    void get_environment_by_id() {
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        Environment environment = environmentRepository.listAll().stream()
                .filter(e -> e.getName().equals("env-metadata-test"))
                .findFirst()
                .orElseThrow();

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId())
                .then()
                .statusCode(200)
                .body("id", equalTo(environment.getId()))
                .body("name", equalTo("env-metadata-test"))
                .body("description", equalTo("description from metadata"))
                .body("status", equalTo("IN_USE"))
                .body("expirationDate", equalTo("2025-12-31"))
                .body("type", equalTo("DESIGN_TIME"))
                .body("role", equalTo("QA"))
                .body("region", equalTo("cm"))
                .body("teams", contains("team-from-metadata"))
                .body("owners", contains("owner from metadata"))
                .body("labels", contains("label1", "label2"))
                .body("accessGroups", contains("group1", "group2"))
                .body("effectiveAccessGroups", contains("group1", "group2", "group3"))
                .body("namespaces", contains(
                        allOf(
                                hasEntry("name", "test-ns"),
                                hasEntry("deployPostfix", "core")
                        )
                ));
    }

    @Test
    @TestSecurity(user = "test")
    void get_environment_by_id_not_found() {
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/environments/non-existent-id")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "test")
    void get_environments_by_project_id() {
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);
        given()
                .when().get("/colly/v2/inventory-service/environments?projectId=solar_earth")
                .then()
                .statusCode(200)
                .body("name", containsInAnyOrder("env-metadata-test", "env-test"));
        given()
                .when().get("/colly/v2/inventory-service/environments?projectId=solar_saturn")
                .then()
                .statusCode(200)
                .body("name", containsInAnyOrder("env-1"));
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
    @TestSecurity(user = "test")
    void get_projects() {
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);
        given()
                .when().get("/colly/v2/inventory-service/projects")
                .then()
                .statusCode(200)
                .body(".",
                        hasItems(
                                allOf(
                                        hasEntry("id", "solar_earth"),
                                        hasEntry("name", "earth"),
                                        hasEntry("type", "PROJECT"),
                                        hasEntry("customerName", "Solar System"),
                                        hasEntry("clusterPlatform", "K8S")
                                ),
                                allOf(
                                        hasEntry("id", "solar_saturn"),
                                        hasEntry("name", "saturn"),
                                        hasEntry("type", "PRODUCT"),
                                        hasEntry("customerName", "Solar System"),
                                        hasEntry("clusterPlatform", "OCP"),
                                        hasEntry("templateRepository", null)
                                )
                        ))
                .body("find { it.id == 'solar_earth' }.instanceRepositories", hasSize(1))
                .body("find { it.id == 'solar_saturn' }.instanceRepositories", hasSize(1))
                .body("find { it.id == 'solar_earth' }.pipelines", hasSize(2))
                .body("find { it.id == 'solar_saturn' }.pipelines", hasSize(2));
    }

    @Test
    @TestSecurity(user = "test")
    void sync_for_particular_project() {
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        Environment environment = environmentRepository.listAll().stream()
                .filter(e -> e.getName().equals("env-metadata-test"))
                .findFirst()
                .orElseThrow();

        environmentRepository.deleteById(environment.getId());

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId())
                .then()
                .statusCode(404);

        given()
                .when().post("/colly/v2/inventory-service/manual-sync?projectId=solar_earth")
                .then()
                .statusCode(204);

        environment = environmentRepository.listAll().stream()
                .filter(e -> e.getName().equals("env-metadata-test"))
                .findFirst()
                .orElseThrow();

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId())
                .then()
                .statusCode(200);
    }

    @Test
    void get_projects_without_auth() {
        given()
                .when().get("/colly/v2/inventory-service/projects")
                .then()
                .statusCode(401);
    }

    @Test
    void get_project_without_auth() {
        given()
                .when().get("/colly/v2/inventory-service/projects/solar_earth")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "test")
    void get_project() {
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);
        given()
                .when().get("/colly/v2/inventory-service/projects/solar_earth")
                .then()
                .statusCode(200)
                .body("id", equalTo("solar_earth"))
                .body("name", equalTo("earth"))
                .body("type", equalTo("PROJECT"))
                .body("customerName", equalTo("Solar System"))
                .body("clusterPlatform", equalTo("K8S"))
                .body("accessGroups", contains("group1", "group2"))
                .body("instanceRepositories", hasItem(
                        allOf(
                                hasEntry("url", "gitrepo_with_cloudpassports"),
                                hasEntry("branch", "main"),
                                hasEntry("token", "earth-envgene-token-789")
                        )
                ))
                .body("templateRepository.url", equalTo("https://gitlab.com/test/templateRepo.git"))
                .body("templateRepository.token", equalTo("test-token"))
                .body("templateRepository.branch", equalTo("main"))
                .body("templateRepository.envgeneArtifact.name", equalTo("my-app:feature-new-ui-123456"))
                .body("templateRepository.envgeneArtifact.templateDescriptorNames", contains("dev", "qa"))
                .body("templateRepository.envgeneArtifact.defaultTemplateDescriptorName", equalTo("dev"))
                .body("pipelines", hasItems(
                        allOf(
                                hasEntry("type", "CLUSTER_PROVISION"),
                                hasEntry("url", "https://github.com/example/cluster-provision-earth"),
                                hasEntry("region", "eu-west-1"),
                                hasEntry("branch", "test"),
                                hasEntry("token", "earth-cluster-token-123")

                        ),
                        allOf(
                                hasEntry("type", "ENV_PROVISION"),
                                hasEntry("url", "https://github.com/example/env-provision-earth"),
                                hasEntry("region", "us-east-1"),
                                hasEntry("branch", null),
                                hasEntry("token", "earth-env-token-456")
                        )
                ));
    }

    @Test
    @TestSecurity(user = "test")
    void get_project_not_found() {
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);
        given()
                .when().get("/colly/v2/inventory-service/projects/non_existent_project")
                .then()
                .statusCode(404);
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
                .body("find { it.name == 'env-metadata-test' }.region", equalTo("cm"))
                .body("find { it.name == 'env-metadata-test' }.owners", emptyIterable())
                .body("find { it.name == 'env-metadata-test' }.expirationDate", nullValue());
    }
}
