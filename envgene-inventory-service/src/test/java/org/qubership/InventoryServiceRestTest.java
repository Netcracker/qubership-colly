package org.qubership;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.colly.MockGitService;
import org.qubership.colly.db.ClusterRepository;
import org.qubership.colly.db.EnvironmentRepository;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;

import java.io.File;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestTransaction
class InventoryServiceRestTest {

    @Inject
    MockGitService mockGitService;

    @Inject
    EnvironmentRepository environmentRepository;

    @Inject
    ClusterRepository clusterRepository;

    @BeforeEach
    void setUp() {
        mockGitService.reset();
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
                .body(".", containsInAnyOrder(
                        allOf(
                                hasEntry("name", "test-cluster"),
                                hasEntry("token", "some_token_for_test_cluster"),
                                hasEntry("cloudApiHost", "https://1E4A399FCB54F505BBA05320EADF0DB3.gr7.eu-west-1.eks.amazonaws.com:443"),
                                hasEntry("cloudPublicHost", "gr7.eu-west-1.eks.amazonaws.com"),
                                hasEntry("monitoringUrl", "http://localhost:8428")
                        ),
                        allOf(
                                hasEntry("name", "unreachable-cluster"),
                                hasEntry("token", "1234567890"),
                                hasEntry("cloudApiHost", "https://some.unreachable.url:8443"),
                                hasEntry("cloudPublicHost", "unreachable.url"),
                                hasEntry("monitoringUrl", "https://vmsingle-victoria.unreachable.url")
                        )))
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
                                hasEntry("cmApproach", "NO_CMDB")
                        ),
                        allOf(
                                hasEntry("name", "env-metadata-test"),
                                hasEntry("description", "description from metadata"),
                                hasEntry("status", "IN_USE"),
                                hasEntry("expirationDate", "2025-12-31"),
                                hasEntry("type", "DESIGN_TIME"),
                                hasEntry("role", "QA"),
                                hasEntry("cmApproach", "CMDB")
                        ),
                        allOf(
                                hasEntry("name", "env-1"),
                                hasEntry("description", "some env for tests")
                        )
                ))
                .body("find { it.name == 'env-metadata-test' }.sspStandalone", equalTo(true))
                .body("find { it.name == 'env-test' }.sspStandalone", equalTo(false))
                .body("find { it.name == 'env-metadata-test' }.teams", contains("team-from-metadata"))
                .body("find { it.name == 'env-metadata-test' }.owners", contains("owner from metadata"));
    }

    @Test
    @TestSecurity(user = "test")
    void get_environment_by_id() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

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
                .body("teams", contains("team-from-metadata"))
                .body("owners", contains("owner from metadata"))
                .body("labels", contains("label1", "label2"))
                .body("accessGroups", contains("group1", "group2"))
                .body("effectiveAccessGroups", contains("group1", "group2", "group3"))
                .body("sspStandalone", equalTo(true))
                .body("cmApproach", equalTo("CMDB"))
                .body("namespaces", containsInAnyOrder(
                        allOf(
                                hasEntry("name", "test-ns"),
                                hasEntry("deployPostfix", "core")
                        ),
                        allOf(
                                hasEntry("name", "test-bss"),
                                hasEntry("deployPostfix", "bss")
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
    @TestSecurity(user = "test")
    void update_environment_with_auth() {
        Environment environment = prepareEnvironmentForTests("env-test");
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
    @TestSecurity(user = "test")
    void update_environment_empty_payload() {
        Environment environment = prepareEnvironmentForTests("env-test");
        given()
                .contentType("application/json")
                .when().patch("/colly/v2/inventory-service/environments/" + environment.getId())
                .then()
                .statusCode(500);
    }

    @Test
    @TestSecurity(user = "test")
    void update_environment_not_found_env() {
        given()
                .contentType("application/json")
                .body("{\"owners\":[\"new-owner\"],\"description\":\"Updated description\",\"labels\":[\"test\",\"test2\"]}")
                .when().patch("/colly/v2/inventory-service/environments/non_existend_env")
                .then()
                .statusCode(404);
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
                                        hasEntry("name", "earth")
                                ),
                                allOf(
                                        hasEntry("id", "solar_saturn"),
                                        hasEntry("name", "saturn")
                                )
                        ))
                .body("find { it.id == 'solar_earth' }.instanceRepositories", hasSize(1))
                .body("find { it.id == 'solar_saturn' }.instanceRepositories", hasSize(1))
                .body("find { it.id == 'solar_saturn' }.templateRepository", nullValue());
    }

    @Test
    @TestSecurity(user = "test")
    void sync_for_particular_project() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

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
                .body("gitGroupUrls", hasSize(2))
                .body("gitGroupUrls.find { it.region == 'cn' }.url", equalTo("https://gitlab.com/solar-system"))
                .body("gitGroupUrls.find { it.region == 'mb' }.url", equalTo("https://gitlab.com/solar-system-mb"))
                .body("instanceRepositories", hasItem(
                        allOf(
                                hasEntry("url", "gitrepo_with_cloudpassports"),
                                hasEntry("branch", "main")
                        )
                ))
                .body("templateRepository.url", equalTo("https://gitlab.com/test/templateRepo.git"))
                .body("templateRepository.branch", equalTo("main"))
                .body("templateRepository.envgeneArtifact.name", equalTo("my-app:feature-new-ui-123456"))
                .body("templateRepository.envgeneArtifact.defaultTemplateDescriptorName", equalTo("dev"));
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
    @TestSecurity(user = "test")
    void update_environment_with_empty_fields() {
        // Setup: sync to get env-metadata-test which has expirationDate = "2025-12-31"
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

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
                .body("find { it.name == 'env-metadata-test' }.owners", emptyIterable())
                .body("find { it.name == 'env-metadata-test' }.expirationDate", nullValue());
    }

    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_environment_level() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters")
                .then()
                .statusCode(200)
                .body("parameters.DEPLOYMENT.ENV_DEPLOY_PARAMETER", equalTo("some value"))
                .body("parameters.RUNTIME.ENV_RUNTIME_PARAMETER", equalTo("some value"))
                .body("parameters.PIPELINE.ENV_PIPELINE_PARAMETER", equalTo("some value"));
    }


    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_environment_level_non_existent_env() {
        prepareEnvironmentForTests("env-metadata-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/non-existent-env/ui-parameters")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_environment_level_env_without_paramsets() {
        Environment environment = prepareEnvironmentForTests("env-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters")
                .then()
                .statusCode(200)
                .body("parameters.DEPLOYMENT", anEmptyMap())
                .body("parameters.RUNTIME", anEmptyMap())
                .body("parameters.PIPELINE", anEmptyMap());
    }

    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_namespace_level() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns")
                .then()
                .statusCode(200)
                .body("parameters.DEPLOYMENT.CORE_DEPLOY_PARAMETER", equalTo("some value"))
                .body("parameters.DEPLOYMENT.CORE_DEPLOY_PARAMETER_2.SECOND_LEVEL_KEY", equalTo("some value"))
                .body("parameters.RUNTIME.CORE_RUNTIME_PARAMETER", equalTo("some value3"))
                .body("parameters.PIPELINE.CORE_PIPELINE_PARAMETER", equalTo("some value2"));
    }

    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_namespace_level_non_existent_namespace() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=invalid-ns")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_namespace_level__namespace_without_paramsets() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-bss")
                .then()
                .statusCode(200)
                .body("parameters.DEPLOYMENT", anEmptyMap())
                .body("parameters.RUNTIME", anEmptyMap())
                .body("parameters.PIPELINE", anEmptyMap());
    }

    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_application_level() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns&applicationName=my-app")
                .then()
                .statusCode(200)
                .body("parameters.DEPLOYMENT.MY_APP_DEPLOY_PARAMETER", equalTo("foo"))
                .body("parameters.RUNTIME.MY_APP_RUNTIME_PARAMETER", equalTo("bar"))
                .body("parameters.PIPELINE", anEmptyMap());
    }

    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_application_level_namespace_without_paramsets() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-bss&applicationName=my-app")
                .then()
                .statusCode(200)
                .body("parameters.DEPLOYMENT", anEmptyMap())
                .body("parameters.RUNTIME", anEmptyMap())
                .body("parameters.PIPELINE", anEmptyMap());
    }

    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_application_level_non_existent_app() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns&applicationName=invalid_app")
                .then()
                .statusCode(200)
                .body("parameters.DEPLOYMENT", anEmptyMap())
                .body("parameters.RUNTIME", anEmptyMap())
                .body("parameters.PIPELINE", anEmptyMap());
    }

    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_no_associated_paramsets_except_one() {
        Environment environment = prepareEnvironmentForTests("env-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters")
                .then()
                .statusCode(200)
                .body("parameters.DEPLOYMENT", anEmptyMap())
                .body("parameters.RUNTIME", anEmptyMap())
                .body("parameters.PIPELINE", anEmptyMap());

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=demo-k8s")
                .then()
                .statusCode(200)
                .body("parameters.DEPLOYMENT.CORE_DEPLOY_PARAMETER", equalTo("some value"))
                .body("parameters.RUNTIME", anEmptyMap())
                .body("parameters.PIPELINE", anEmptyMap());
    }

    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_no_associated_paramsets() {
        Environment environment = prepareEnvironmentForTests("env-1");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters")
                .then()
                .statusCode(200)
                .body("parameters.DEPLOYMENT", anEmptyMap())
                .body("parameters.RUNTIME", anEmptyMap())
                .body("parameters.PIPELINE", anEmptyMap());

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=demo-k8s")
                .then()
                .statusCode(200)
                .body("parameters.DEPLOYMENT", anEmptyMap())
                .body("parameters.RUNTIME", anEmptyMap())
                .body("parameters.PIPELINE", anEmptyMap());
    }

    @Test
    @TestSecurity(user = "test")
    void set_ui_parameters_environment_level() {
        Environment environment = prepareEnvironmentForTests("env-test");

        given()
                .contentType("application/json")
                .body("{\"commitInfo\": {\"username\": \"test\", \"email\": \"test@mail.com\", \"commitMessage\": \"test\"}," +
                        "\"parameters\": {" +
                        "\"DEPLOYMENT\":{\"NEW_ENV_DEPLOY_PARAMETER\":\"some value1\"}," +
                        "\"RUNTIME\":{\"NEW_ENV_RUNTIME_PARAMETER\":\"some value2\"}," +
                        "\"PIPELINE\":{\"NEW_ENV_PIPELINE_PARAMETER\":\"some value3\"}" +
                        "}}")
                .when().post("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters")
                .then()
                .statusCode(204);


        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters")
                .then()
                .statusCode(200)
                .body("parameters.DEPLOYMENT.NEW_ENV_DEPLOY_PARAMETER", equalTo("some value1"))
                .body("parameters.RUNTIME.NEW_ENV_RUNTIME_PARAMETER", equalTo("some value2"))
                .body("parameters.PIPELINE.NEW_ENV_PIPELINE_PARAMETER", equalTo("some value3"));

    }

    @Test
    @TestSecurity(user = "test")
    void set_ui_parameters_env_not_found() {
        given()
                .contentType("application/json")
                .body("{\"commitInfo\": {\"username\": \"test\", \"email\": \"test@mail.com\", \"commitMessage\": \"test\"}," +
                        "\"parameters\": {" +
                        "\"DEPLOYMENT\":{\"NEW_ENV_DEPLOY_PARAMETER\":\"some value1\"}," +
                        "\"RUNTIME\":{\"NEW_ENV_RUNTIME_PARAMETER\":\"some value2\"}," +
                        "\"PIPELINE\":{\"NEW_ENV_PIPELINE_PARAMETER\":\"some value3\"}" +
                        "}}")
                .when().post("/colly/v2/inventory-service/environments/non-existent-env/ui-parameters")
                .then()
                .statusCode(404);
    }


    @Test
    @TestSecurity(user = "test")
    void set_ui_parameters_empty_input() {
        Environment environment = prepareEnvironmentForTests("env-test");
        given()
                .contentType("application/json")
                .when().post("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters")
                .then()
                .statusCode(500);
    }

    @Test
    @TestSecurity(user = "test")
    void set_ui_parameters_null_parameters_field() {
        Environment environment = prepareEnvironmentForTests("env-test");
        given()
                .contentType("application/json")
                .body("{\"commitInfo\": {\"username\": \"test\", \"email\": \"test@mail.com\", \"commitMessage\": \"test\"}," +
                        "\"parameters\": null}")
                .when().post("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test")
    void set_ui_parameters_env_without_paramsets() {
        Environment environment = prepareEnvironmentForTests("env-1");
        given()
                .contentType("application/json")
                .body("{\"commitInfo\": {\"username\": \"test\", \"email\": \"test@mail.com\", \"commitMessage\": \"test\"}," +
                        "\"parameters\": {" +
                        "\"DEPLOYMENT\":{\"NEW_ENV_DEPLOY_PARAMETER\":\"some value1\"}," +
                        "\"RUNTIME\":{\"NEW_ENV_RUNTIME_PARAMETER\":\"some value2\"}," +
                        "\"PIPELINE\":{\"NEW_ENV_PIPELINE_PARAMETER\":\"some value3\"}" +
                        "}}")
                .when().post("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters")
                .then()
                .statusCode(200)
                .body("parameters.DEPLOYMENT.NEW_ENV_DEPLOY_PARAMETER", equalTo("some value1"))
                .body("parameters.RUNTIME.NEW_ENV_RUNTIME_PARAMETER", equalTo("some value2"))
                .body("parameters.PIPELINE.NEW_ENV_PIPELINE_PARAMETER", equalTo("some value3"));


    }


    @Test
    @TestSecurity(user = "test")
    void set_ui_parameters_namespace_level() {
        Environment environment = prepareEnvironmentForTests("env-test");

        given()
                .contentType("application/json")
                .body("{\"commitInfo\": {\"username\": \"test\", \"email\": \"test@mail.com\", \"commitMessage\": \"test\"}," +
                        "\"parameters\": {" +
                        "\"DEPLOYMENT\":{\"NEW_NS_DEPLOY_PARAMETER\":\"some value1\"}," +
                        "\"RUNTIME\":{\"NEW_NS_RUNTIME_PARAMETER\":\"some value2\"}," +
                        "\"PIPELINE\":{\"NEW_NS_PIPELINE_PARAMETER\":\"some value3\"}" +
                        "}}")
                .when().post("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=demo-k8s")
                .then()
                .statusCode(204);


        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=demo-k8s")
                .then()
                .statusCode(200)
                .body("parameters.DEPLOYMENT.NEW_NS_DEPLOY_PARAMETER", equalTo("some value1"))
                .body("parameters.RUNTIME.NEW_NS_RUNTIME_PARAMETER", equalTo("some value2"))
                .body("parameters.PIPELINE.NEW_NS_PIPELINE_PARAMETER", equalTo("some value3"));
    }

    @Test
    @TestSecurity(user = "test")
    void set_ui_parameters_namespace_level_non_existent_namespace() {
        Environment environment = prepareEnvironmentForTests("env-test");

        given()
                .contentType("application/json")
                .body("{\"commitInfo\": {\"username\": \"test\", \"email\": \"test@mail.com\", \"commitMessage\": \"test\"}," +
                        "\"parameters\": {" +
                        "\"DEPLOYMENT\":{\"NEW_NS_DEPLOY_PARAMETER\":\"some value1\"}," +
                        "\"RUNTIME\":{\"NEW_NS_RUNTIME_PARAMETER\":\"some value2\"}," +
                        "\"PIPELINE\":{\"NEW_NS_PIPELINE_PARAMETER\":\"some value3\"}" +
                        "}}")
                .when().post("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=non-existent-ns")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "test")
    void set_ui_parameters_application_level() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .contentType("application/json")
                .body("{\"commitInfo\": {\"username\": \"test\", \"email\": \"test@mail.com\", \"commitMessage\": \"test\"}," +
                        "\"parameters\": {" +
                        "\"DEPLOYMENT\":{\"NEW_NS_DEPLOY_PARAMETER\":\"some value1\"}," +
                        "\"RUNTIME\":{\"NEW_NS_RUNTIME_PARAMETER\":\"some value2\"}" +
                        "}}")
                .when().post("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns&applicationName=my-app")
                .then()
                .statusCode(204);


        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns&applicationName=my-app")
                .then()
                .statusCode(200)
                .body("parameters.DEPLOYMENT.NEW_NS_DEPLOY_PARAMETER", equalTo("some value1"))
                .body("parameters.RUNTIME.NEW_NS_RUNTIME_PARAMETER", equalTo("some value2"))
                .body("parameters.PIPELINE", anEmptyMap());
    }

    @Test
    @TestSecurity(user = "test")
    void set_ui_parameters_application_level_pipeline_context_is_not_allowed() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .contentType("application/json")
                .body("{\"commitInfo\": {\"username\": \"test\", \"email\": \"test@mail.com\", \"commitMessage\": \"test\"}," +
                        "\"parameters\": {" +
                        "\"DEPLOYMENT\":{\"NEW_NS_DEPLOY_PARAMETER\":\"some value1\"}," +
                        "\"RUNTIME\":{\"NEW_NS_RUNTIME_PARAMETER\":\"some value2\"}," +
                        "\"PIPELINE\":{\"NEW_NS_PIPELINE_PARAMETER\":\"some value3\"}" +
                        "}}")
                .when().post("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns&applicationName=my-app")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test")
    void set_ui_parameters_application_level_empty_values() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .contentType("application/json")
                .body("{\"commitInfo\": {\"username\": \"test\", \"email\": \"test@mail.com\", \"commitMessage\": \"test\"}," +
                        "\"parameters\": {" +
                        "\"DEPLOYMENT\":{}," +
                        "\"RUNTIME\":{}" +
                        "}}")
                .when().post("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns")
                .then()
                .statusCode(200)
                .body("parameters.DEPLOYMENT", anEmptyMap())
                .body("parameters.RUNTIME", anEmptyMap());
    }


    @Test
    @TestSecurity(user = "test")
    void sync_removes_deleted_environments_from_cache() {
        // First sync: test-cluster has env-test and env-metadata-test
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/environments")
                .then()
                .statusCode(200)
                .body("name", hasItems("env-test", "env-metadata-test"));

        // mock: clone as usual, but remove env-metadata-test from the destination
        mockGitService.setCloneAction((url, dest) -> {
            FileUtils.copyDirectory(new File("src/test/resources/" + url), dest);
            FileUtils.deleteDirectory(new File(dest, "test-cluster/env-metadata-test"));
        });

        // Second sync: env-metadata-test should be removed from cache
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/environments")
                .then()
                .statusCode(200)
                .body("name", hasItem("env-test"))
                .body("name", not(hasItem("env-metadata-test")));
    }

    @Test
    @TestSecurity(user = "test")
    void sync_removes_deleted_project_from_cache() {
        // First sync: both solar_earth and solar_saturn projects are loaded
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/projects")
                .then()
                .statusCode(200)
                .body("id", hasItems("solar_earth", "solar_saturn"));

        // mock: clone as usual, but remove solar_earth project folder
        mockGitService.setCloneAction((url, dest) -> {
            FileUtils.copyDirectory(new File("src/test/resources/" + url), dest);
            FileUtils.deleteDirectory(new File(dest, "projects/solar_earth"));
        });

        // Second sync: solar_earth should be removed from cache
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/projects")
                .then()
                .statusCode(200)
                .body("id", hasItem("solar_saturn"))
                .body("id", not(hasItem("solar_earth")));
    }

    @Test
    @TestSecurity(user = "test")
    void sync_removes_clusters_and_environments_when_project_deleted() {
        // First sync: test-cluster (earth) and unreachable-cluster (saturn) are loaded
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/clusters")
                .then()
                .statusCode(200)
                .body("name", hasItems("test-cluster", "unreachable-cluster"));

        given()
                .when().get("/colly/v2/inventory-service/environments")
                .then()
                .statusCode(200)
                .body("name", hasItems("env-test", "env-metadata-test", "env-1"));

        // mock: clone as usual, but remove solar_earth project folder
        mockGitService.setCloneAction((url, dest) -> {
            FileUtils.copyDirectory(new File("src/test/resources/" + url), dest);
            FileUtils.deleteDirectory(new File(dest, "projects/solar_earth"));
        });

        // Second sync: test-cluster and its environments should be removed, unreachable-cluster and env-1 remain
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/clusters")
                .then()
                .statusCode(200)
                .body("name", hasItem("unreachable-cluster"))
                .body("name", not(hasItem("test-cluster")));

        given()
                .when().get("/colly/v2/inventory-service/environments")
                .then()
                .statusCode(200)
                .body("name", hasItem("env-1"))
                .body("name", not(hasItems("env-test", "env-metadata-test")));
    }

    @Test
    void liveness_probe_returns_up() {
        given()
                .when().get("/q/health/live")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void readiness_probe_returns_up() {
        given()
                .when().get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    private @NotNull Environment prepareEnvironmentForTests(String envName) {
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        return environmentRepository.listAll().stream()
                .filter(e -> e.getName().equals(envName))
                .findFirst()
                .orElseThrow();
    }
}

