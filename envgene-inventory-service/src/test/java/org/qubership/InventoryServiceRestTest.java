package org.qubership;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.colly.MockGitService;
import org.qubership.colly.cloudpassport.GitInfo;
import org.qubership.colly.db.ClusterRepository;
import org.qubership.colly.db.EnvironmentRepository;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;

import java.io.File;
import java.io.IOException;

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

    @Inject
    RedisDataSource redisDataSource;

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
                        hasEntry("name", "env-1"),
                        hasEntry("name", "env-no-cmdb-v2-test"),
                        hasEntry("name", "env-cmdb-with-v2-override-test"),
                        hasEntry("name", "env-cmdb-with-v1-override-test"),
                        hasEntry("name", "env-no-cmdb-v1-explicit-test")
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
                        containsInAnyOrder("env-test", "env-metadata-test", "env-no-cmdb-v2-test",
                                "env-cmdb-with-v2-override-test", "env-cmdb-with-v1-override-test",
                                "env-no-cmdb-v1-explicit-test"))
                .body("find { it.name == 'test-cluster' }.dashboardUrl",
                        equalTo("https://dashboard.example.com"))
                .body("find { it.name == 'test-cluster' }.dbaasUrl",
                        equalTo("https://dbaas.example.com"))
                .body("find { it.name == 'test-cluster' }.deployerUrl",
                        equalTo("https://deployer.example.com"))
                .body("find { it.name == 'test-cluster' }.argoUrl",
                        equalTo("https://argo.example.com"))
                .body("find { it.name == 'test-cluster' }.cloudPublicHost",
                        equalTo("gr7.eu-west-1.eks.amazonaws.com"))
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
                .body("cloudPublicHost", equalTo("gr7.eu-west-1.eks.amazonaws.com"))
                .body("environments.name", containsInAnyOrder("env-test", "env-metadata-test",
                        "env-no-cmdb-v2-test", "env-cmdb-with-v2-override-test",
                        "env-cmdb-with-v1-override-test", "env-no-cmdb-v1-explicit-test"))
                .body("environments", hasSize(6));
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
    void getClusters_sameNameInTwoProjects_areKeptAsDistinctClusters() {
        // solar_saturn's repo gets its own "test-cluster" folder, distinct from solar_earth's,
        // reproducing two projects that legitimately each have a cluster with the same name.
        mockGitService.setCloneAction((repoName, dest) -> {
            FileUtils.copyDirectory(new File("src/test/resources/" + repoName), dest);
            if ("gitrepo_with_unreachable_cluster".equals(repoName)) {
                writeMinimalClusterCloudPassport(new File(dest, "environments/test-cluster"),
                        "saturn-region", "saturn_token_for_test_cluster");
            }
        });

        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/clusters?projectId=solar_earth")
                .then()
                .statusCode(200)
                .body("name", hasItem("test-cluster"))
                .body("find { it.name == 'test-cluster' }.region", equalTo("cm"));

        given()
                .when().get("/colly/v2/inventory-service/clusters?projectId=solar_saturn")
                .then()
                .statusCode(200)
                .body("name", containsInAnyOrder("test-cluster", "unreachable-cluster"))
                .body("find { it.name == 'test-cluster' }.region", equalTo("saturn-region"));

        given()
                .when().get("/colly/v2/inventory-service/clusters")
                .then()
                .statusCode(200)
                .body("findAll { it.name == 'test-cluster' }", hasSize(2));
    }

    @Test
    @TestSecurity(user = "test")
    void getClusters_clusterMovedBetweenProjects_onlyVisibleInNewProject() {
        // First sync: test-cluster lives only in solar_earth's repo (base fixtures, unmodified).
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/clusters?projectId=solar_earth")
                .then()
                .statusCode(200)
                .body("name", hasItem("test-cluster"));

        given()
                .when().get("/colly/v2/inventory-service/clusters?projectId=solar_saturn")
                .then()
                .statusCode(200)
                .body("name", not(hasItem("test-cluster")));

        // Simulate the move: removed from solar_earth's repo, committed with the same name into solar_saturn's repo.
        mockGitService.setCloneAction((repoName, dest) -> {
            FileUtils.copyDirectory(new File("src/test/resources/" + repoName), dest);
            if ("gitrepo_with_cloudpassports".equals(repoName)) {
                FileUtils.deleteDirectory(new File(dest, "environments/test-cluster"));
            } else if ("gitrepo_with_unreachable_cluster".equals(repoName)) {
                writeMinimalClusterCloudPassport(new File(dest, "environments/test-cluster"),
                        "moved-region", "moved_token_for_test_cluster");
            }
        });

        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/clusters?projectId=solar_earth")
                .then()
                .statusCode(200)
                .body("name", not(hasItem("test-cluster")));

        given()
                .when().get("/colly/v2/inventory-service/clusters?projectId=solar_saturn")
                .then()
                .statusCode(200)
                .body("name", hasItem("test-cluster"));

        given()
                .when().get("/colly/v2/inventory-service/clusters")
                .then()
                .statusCode(200)
                .body("findAll { it.name == 'test-cluster' }", hasSize(1));
    }

    @Test
    @TestSecurity(user = "test")
    void getClusters_migrationFromLegacyNameIndex_doesNotCauseCrossProjectCollision() {
        // Use a cluster name that appears in neither project's real fixtures, so this test's
        // Redis side effects can't collide with "test-cluster"/"unreachable-cluster" state that
        // other tests in this class depend on staying stable across the whole suite run (Redis
        // isn't reset between test methods) - and so removeDeletedClusters cleans this name back
        // out on the very next default-fixture sync, exactly like the other two new tests below.
        String clusterName = "legacy-migrated-cluster";

        // Simulate a cluster persisted by the OLD (pre-fix) code: a single cached record
        // reachable only via the global, non-project-scoped name index - no scoped
        // by-name:<projectId>:<name> entry yet, as if this row predates deploying the fix.
        Cluster legacyCluster = Cluster.builder()
                .name(clusterName)
                .gitInfo(new GitInfo(null, null, "solar_earth"))
                .token("legacy_token_before_fix")
                .region("legacy-region-before-fix")
                .build();
        clusterRepository.persist(legacyCluster); // also writes the new scoped index + project set...
        String legacyId = legacyCluster.getId();
        // ...so strip those back down to a pre-fix-only state: just the record and the old global index.
        redisDataSource.key(String.class).del("inventory:idx:clusters:by-name:solar_earth:" + clusterName);
        redisDataSource.value(String.class, String.class).set("inventory:idx:clusters:by-name:" + clusterName, legacyId);

        // Both projects now report a genuinely different cluster under that same name - the
        // exact scenario this fix targets, except this time one side of the collision is the
        // pre-existing legacy-indexed record above instead of a plain empty cache.
        mockGitService.setCloneAction((repoName, dest) -> {
            FileUtils.copyDirectory(new File("src/test/resources/" + repoName), dest);
            if ("gitrepo_with_cloudpassports".equals(repoName)) {
                writeMinimalClusterCloudPassport(new File(dest, "environments/" + clusterName),
                        "earth-post-migration-region", "earth_post_migration_token");
            } else if ("gitrepo_with_unreachable_cluster".equals(repoName)) {
                writeMinimalClusterCloudPassport(new File(dest, "environments/" + clusterName),
                        "saturn-post-migration-region", "saturn_post_migration_token");
            }
        });

        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        // Both projects' cluster must keep their own, non-cross-contaminated data...
        given()
                .when().get("/colly/v2/inventory-service/clusters?projectId=solar_earth")
                .then()
                .statusCode(200)
                .body("find { it.name == '" + clusterName + "' }.region", equalTo("earth-post-migration-region"));

        given()
                .when().get("/colly/v2/inventory-service/clusters?projectId=solar_saturn")
                .then()
                .statusCode(200)
                .body("find { it.name == '" + clusterName + "' }.region", equalTo("saturn-post-migration-region"));

        // ...as two genuinely distinct records: exactly one of them adopted the pre-existing
        // legacy id, the other must have been created fresh - not both sharing the same id.
        given()
                .when().get("/colly/v2/inventory-service/clusters")
                .then()
                .statusCode(200)
                .body("findAll { it.name == '" + clusterName + "' }", hasSize(2))
                .body("findAll { it.name == '" + clusterName + "' }.id", hasItem(legacyId));
    }

    @Test
    @TestSecurity(user = "test")
    void getEnvironments_environmentMovesToClusterWithSameNameInDifferentProject() {
        // First sync: env-test lives under solar_earth's real "test-cluster" (base fixtures, unmodified).
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/environments?projectId=solar_earth")
                .then()
                .statusCode(200)
                .body("name", hasItem("env-test"));

        given()
                .when().get("/colly/v2/inventory-service/environments?projectId=solar_saturn")
                .then()
                .statusCode(200)
                .body("name", not(hasItem("env-test")));

        Cluster earthClusterBeforeMove = clusterRepository.findByProjectIdAndName("solar_earth", "test-cluster");

        // Simulate the move: env-test is removed from solar_earth's "test-cluster" and committed
        // under a cluster with the SAME name ("test-cluster") in solar_saturn's repo - a distinct
        // cluster record from solar_earth's, since cluster identity is now scoped per project.
        mockGitService.setCloneAction((repoName, dest) -> {
            FileUtils.copyDirectory(new File("src/test/resources/" + repoName), dest);
            if ("gitrepo_with_cloudpassports".equals(repoName)) {
                FileUtils.deleteDirectory(new File(dest, "environments/test-cluster/env-test"));
            } else if ("gitrepo_with_unreachable_cluster".equals(repoName)) {
                writeMinimalClusterCloudPassport(new File(dest, "environments/test-cluster"),
                        "saturn-region-for-moved-env", "saturn_token_for_test_cluster");
                writeMinimalEnvironment(new File(dest, "environments/test-cluster/env-test"), "some env for tests");
            }
        });

        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        // env-test must have followed the move: gone from solar_earth, present under solar_saturn.
        given()
                .when().get("/colly/v2/inventory-service/environments?projectId=solar_earth")
                .then()
                .statusCode(200)
                .body("name", not(hasItem("env-test")));

        given()
                .when().get("/colly/v2/inventory-service/environments?projectId=solar_saturn")
                .then()
                .statusCode(200)
                .body("name", hasItem("env-test"));

        given()
                .when().get("/colly/v2/inventory-service/environments")
                .then()
                .statusCode(200)
                .body("findAll { it.name == 'env-test' }", hasSize(1));

        // ...and it must be attached to solar_saturn's own "test-cluster" record, not to
        // solar_earth's (which kept its identity - it still exists, just minus this one env).
        Cluster saturnCluster = clusterRepository.findByProjectIdAndName("solar_saturn", "test-cluster");
        given()
                .when().get("/colly/v2/inventory-service/environments?projectId=solar_saturn")
                .then()
                .statusCode(200)
                .body("find { it.name == 'env-test' }.cluster.id", equalTo(saturnCluster.getId()))
                .body("find { it.name == 'env-test' }.cluster.id", not(equalTo(earthClusterBeforeMove.getId())));

        given()
                .when().get("/colly/v2/inventory-service/clusters")
                .then()
                .statusCode(200)
                .body("findAll { it.name == 'test-cluster' }", hasSize(2));
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
                        ),
                        allOf(
                                hasEntry("name", "env-no-cmdb-v2-test"),
                                hasEntry("cmApproach", "NO_CMDB_V2")
                        ),
                        allOf(
                                hasEntry("name", "env-cmdb-with-v2-override-test"),
                                hasEntry("cmApproach", "NO_CMDB_V2")
                        ),
                        allOf(
                                hasEntry("name", "env-cmdb-with-v1-override-test"),
                                hasEntry("cmApproach", "NO_CMDB")
                        ),
                        allOf(
                                hasEntry("name", "env-no-cmdb-v1-explicit-test"),
                                hasEntry("cmApproach", "NO_CMDB")
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
                .body("effectiveSetHistoryUrl", equalTo("gitrepo_with_cloudpassports/commits/main/environments/test-cluster/env-metadata-test/effective-set"))
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
                .body("name", containsInAnyOrder("env-metadata-test", "env-test",
                        "env-no-cmdb-v2-test", "env-cmdb-with-v2-override-test",
                        "env-cmdb-with-v1-override-test", "env-no-cmdb-v1-explicit-test"));
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
                .body("parameters.deployment.ENV_DEPLOY_PARAMETER", equalTo("some value"))
                .body("parameters.runtime.ENV_RUNTIME_PARAMETER", equalTo("some value"))
                .body("parameters.pipeline.ENV_PIPELINE_PARAMETER", equalTo("some value"));
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
                .body("parameters.deployment.ENV_GLOBAL_PARAM", equalTo("global level value"))
                .body("parameters.runtime", anEmptyMap())
                .body("parameters.pipeline", anEmptyMap());
    }

    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_namespace_level() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns")
                .then()
                .statusCode(200)
                .body("parameters.deployment.CORE_DEPLOY_PARAMETER", equalTo("some value"))
                .body("parameters.deployment.CORE_DEPLOY_PARAMETER_2.SECOND_LEVEL_KEY", equalTo("some value"))
                .body("parameters.runtime.CORE_RUNTIME_PARAMETER", equalTo("some value3"))
                .body("parameters.pipeline.CORE_PIPELINE_PARAMETER", equalTo("some value2"));
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
                .body("parameters.deployment", anEmptyMap())
                .body("parameters.runtime", anEmptyMap())
                .body("parameters.pipeline", anEmptyMap());
    }

    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_application_level() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns&applicationName=my-app")
                .then()
                .statusCode(200)
                .body("parameters.deployment.MY_APP_DEPLOY_PARAMETER", equalTo("foo"))
                .body("parameters.runtime.MY_APP_RUNTIME_PARAMETER", equalTo("barManual"))
                .body("parameters.pipeline", anEmptyMap());
    }

    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_application_level_second_app() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns&applicationName=my-second-app")
                .then()
                .statusCode(200)
                .body("parameters.deployment.MY_APP_DEPLOY_PARAMETER", equalTo("bar2"))
                .body("parameters.pipeline", anEmptyMap());
    }

    @Test
    @TestSecurity(user = "test")
    void set_ui_parameters_application_level_does_not_affect_other_app() throws Exception {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .contentType("application/json")
                .body("{\"commitInfo\": {\"username\": \"test\", \"email\": \"test@mail.com\", \"commitMessage\": \"test\"}," +
                        "\"parameters\": {" +
                        "\"deployment\":{\"MY_APP_DEPLOY_PARAMETER\":\"barUpdated\"}" +
                        "}}")
                .when().post("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns&applicationName=my-app")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns&applicationName=my-app")
                .then()
                .statusCode(200)
                .body("parameters.deployment.MY_APP_DEPLOY_PARAMETER", equalTo("barUpdated"));

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns&applicationName=my-second-app")
                .then()
                .statusCode(200)
                .body("parameters.deployment.MY_APP_DEPLOY_PARAMETER", equalTo("bar2"));

        Cluster cluster = clusterRepository.listAll().stream()
                .filter(c -> c.getName().equals("test-cluster"))
                .findFirst().orElseThrow();
        File envDefFile = new File(cluster.getGitInfo().folderName() + "/environments/test-cluster/env-metadata-test/Inventory/env_definition.yml");
        System.out.println("=== env_definition.yml after parameter update ===\n" + FileUtils.readFileToString(envDefFile, "UTF-8"));
    }

    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_application_level_namespace_without_paramsets() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-bss&applicationName=my-app")
                .then()
                .statusCode(200)
                .body("parameters.deployment", anEmptyMap())
                .body("parameters.runtime", anEmptyMap())
                .body("parameters.pipeline", anEmptyMap());
    }

    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_namespace_level_includes_generic_paramsets() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns")
                .then()
                .statusCode(200)
                .body("parameters.deployment.GENERIC_NAMESPACE_PARAM", equalTo("namespace value"))
                // ui-override params are still present
                .body("parameters.deployment.CORE_DEPLOY_PARAMETER", equalTo("some value"));
    }

    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_both_levels_from_single_paramset_file() {
        // core-mixed-paramset.yaml has both `parameters` and `applications` sections.
        // The same file must produce NAMESPACE-level params (for namespace requests)
        // and APPLICATION-level params (for application requests).
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns")
                .then()
                .statusCode(200)
                .body("parameters.deployment.GENERIC_NAMESPACE_PARAM", equalTo("namespace value"));

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns&applicationName=my-app")
                .then()
                .statusCode(200)
                .body("parameters.deployment.GENERIC_APP_PARAM", equalTo("app value"));
    }

    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_same_key_different_value_per_level() {
        // mixed-paramset-same-parameter.yaml has the same key PARAM in both
        // `parameters` (namespace level) and `applications[my-app]` (application level).
        // Namespace request must return the namespace value; application request — the application value.
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns")
                .then()
                .statusCode(200)
                .body("parameters.deployment.PARAM", equalTo("namespace value"));

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns&applicationName=my-app")
                .then()
                .statusCode(200)
                .body("parameters.deployment.PARAM", equalTo("app value"));
    }

    @Test
    @TestSecurity(user = "test")
    void set_ui_parameters_overridden_by_later_paramset_after_sync() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        // 1. POST LATE_PARAM="api-value-1" → written to core-deploy-ui-override;
        //    updateParamset replaces all NAMESPACE/core/DEPLOYMENT paramsets in memory with the single ui-override entry
        given()
                .contentType("application/json")
                .body("{\"commitInfo\": {\"username\": \"test\", \"email\": \"test@mail.com\", \"commitMessage\": \"test\"}," +
                        "\"parameters\": {\"deployment\":{\"LATE_PARAM\":\"api-value-1\"}}}")
                .when().post("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns")
                .then()
                .statusCode(200)
                .body("parameters.deployment.LATE_PARAM", equalTo("api-value-1"));

        // 2. Second sync: inject late-paramset into env_definition.yml (after core-second-param, i.e. last in core list).
        //    late-paramset.yaml pre-exists in the repo and declares LATE_PARAM="from-late-paramset".
        //    core-deploy-ui-override.yaml is reverted to its original content (no LATE_PARAM) by the clone.
        //    After reload, late-paramset is the last for core/DEPLOYMENT → it wins over ui-override.

        mockGitService.setCloneAction((repoName, dest) -> {
            FileUtils.copyDirectory(new File("src/test/resources/" + repoName), dest);
            if ("gitrepo_with_cloudpassports".equals(repoName)) {
                File envDef = new File(dest, "environments/test-cluster/env-metadata-test/Inventory/env_definition.yml");
                String content = FileUtils.readFileToString(envDef, "UTF-8");
                content = content.replace(
                        "      - core-second-param\n",
                        "      - core-second-param\n      - late-paramset\n"
                );
                FileUtils.writeStringToFile(envDef, content, "UTF-8");
            }
        });

        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns")
                .then()
                .statusCode(200)
                .body("parameters.deployment.LATE_PARAM", equalTo("from-late-paramset"));

        // 3. POST LATE_PARAM="api-value-2" → updateParamset removes ALL NAMESPACE/core/DEPLOYMENT paramsets
        //    from memory (including late-paramset) and appends the ui-override entry at the END → ui-override wins
        given()
                .contentType("application/json")
                .body("{\"commitInfo\": {\"username\": \"test\", \"email\": \"test@mail.com\", \"commitMessage\": \"test\"}," +
                        "\"parameters\": {\"deployment\":{\"LATE_PARAM\":\"api-value-2\"}}}")
                .when().post("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns")
                .then()
                .statusCode(200)
                .body("parameters.deployment.LATE_PARAM", equalTo("api-value-2"));
    }

    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_last_paramset_wins_on_duplicate_key() {
        // core-first-param declares DUPLICATE_PARAM="first value",
        // core-second-param (listed after it) declares DUPLICATE_PARAM="second value".
        // The last paramset in env_definition.yml must win.
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns")
                .then()
                .statusCode(200)
                .body("parameters.deployment.DUPLICATE_PARAM", equalTo("second value"));
    }

    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_application_level_non_existent_app() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns&applicationName=invalid_app")
                .then()
                .statusCode(200)
                .body("parameters.deployment", anEmptyMap())
                .body("parameters.runtime", anEmptyMap())
                .body("parameters.pipeline", anEmptyMap());
    }

    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_no_associated_paramsets_except_one() {
        Environment environment = prepareEnvironmentForTests("env-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters")
                .then()
                .statusCode(200)
                .body("parameters.deployment.ENV_GLOBAL_PARAM", equalTo("global level value"))
                .body("parameters.runtime", anEmptyMap())
                .body("parameters.pipeline", anEmptyMap());

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=demo-k8s")
                .then()
                .statusCode(200)
                .body("parameters.deployment.CORE_DEPLOY_PARAMETER", equalTo("some value"))
                .body("parameters.runtime", anEmptyMap())
                .body("parameters.pipeline", anEmptyMap());
    }

    @Test
    @TestSecurity(user = "test")
    void get_ui_parameters_no_associated_paramsets() {
        Environment environment = prepareEnvironmentForTests("env-1");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters")
                .then()
                .statusCode(200)
                .body("parameters.deployment", anEmptyMap())
                .body("parameters.runtime", anEmptyMap())
                .body("parameters.pipeline", anEmptyMap());

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=demo-k8s")
                .then()
                .statusCode(200)
                .body("parameters.deployment", anEmptyMap())
                .body("parameters.runtime", anEmptyMap())
                .body("parameters.pipeline", anEmptyMap());
    }

    @Test
    @TestSecurity(user = "test")
    void set_ui_parameters_environment_level() {
        Environment environment = prepareEnvironmentForTests("env-test");

        given()
                .contentType("application/json")
                .body("{\"commitInfo\": {\"username\": \"test\", \"email\": \"test@mail.com\", \"commitMessage\": \"test\"}," +
                        "\"parameters\": {" +
                        "\"deployment\":{\"NEW_ENV_DEPLOY_PARAMETER\":\"some value1\"}," +
                        "\"runtime\":{\"NEW_ENV_RUNTIME_PARAMETER\":\"some value2\"}," +
                        "\"pipeline\":{\"NEW_ENV_PIPELINE_PARAMETER\":\"some value3\"}" +
                        "}}")
                .when().post("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters")
                .then()
                .statusCode(204);


        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters")
                .then()
                .statusCode(200)
                .body("parameters.deployment.NEW_ENV_DEPLOY_PARAMETER", equalTo("some value1"))
                .body("parameters.runtime.NEW_ENV_RUNTIME_PARAMETER", equalTo("some value2"))
                .body("parameters.pipeline.NEW_ENV_PIPELINE_PARAMETER", equalTo("some value3"));

    }

    @Test
    @TestSecurity(user = "test")
    void set_ui_parameters_env_not_found() {
        given()
                .contentType("application/json")
                .body("{\"commitInfo\": {\"username\": \"test\", \"email\": \"test@mail.com\", \"commitMessage\": \"test\"}," +
                        "\"parameters\": {" +
                        "\"deployment\":{\"NEW_ENV_DEPLOY_PARAMETER\":\"some value1\"}," +
                        "\"runtime\":{\"NEW_ENV_RUNTIME_PARAMETER\":\"some value2\"}," +
                        "\"pipeline\":{\"NEW_ENV_PIPELINE_PARAMETER\":\"some value3\"}" +
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
                        "\"deployment\":{\"NEW_ENV_DEPLOY_PARAMETER\":\"some value1\"}," +
                        "\"runtime\":{\"NEW_ENV_RUNTIME_PARAMETER\":\"some value2\"}," +
                        "\"pipeline\":{\"NEW_ENV_PIPELINE_PARAMETER\":\"some value3\"}" +
                        "}}")
                .when().post("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters")
                .then()
                .statusCode(200)
                .body("parameters.deployment.NEW_ENV_DEPLOY_PARAMETER", equalTo("some value1"))
                .body("parameters.runtime.NEW_ENV_RUNTIME_PARAMETER", equalTo("some value2"))
                .body("parameters.pipeline.NEW_ENV_PIPELINE_PARAMETER", equalTo("some value3"));


    }


    @Test
    @TestSecurity(user = "test")
    void set_ui_parameters_namespace_level() {
        Environment environment = prepareEnvironmentForTests("env-test");

        given()
                .contentType("application/json")
                .body("{\"commitInfo\": {\"username\": \"test\", \"email\": \"test@mail.com\", \"commitMessage\": \"test\"}," +
                        "\"parameters\": {" +
                        "\"deployment\":{\"NEW_NS_DEPLOY_PARAMETER\":\"some value1\"}," +
                        "\"runtime\":{\"NEW_NS_RUNTIME_PARAMETER\":\"some value2\"}" +
                        "}}")
                .when().post("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=demo-k8s")
                .then()
                .statusCode(204);


        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=demo-k8s")
                .then()
                .statusCode(200)
                .body("parameters.deployment.NEW_NS_DEPLOY_PARAMETER", equalTo("some value1"))
                .body("parameters.runtime.NEW_NS_RUNTIME_PARAMETER", equalTo("some value2"))
                .body("parameters.pipeline", anEmptyMap());
    }

    @Test
    @TestSecurity(user = "test")
    void set_ui_parameters_namespace_level_non_existent_namespace() {
        Environment environment = prepareEnvironmentForTests("env-test");

        given()
                .contentType("application/json")
                .body("{\"commitInfo\": {\"username\": \"test\", \"email\": \"test@mail.com\", \"commitMessage\": \"test\"}," +
                        "\"parameters\": {" +
                        "\"deployment\":{\"NEW_NS_DEPLOY_PARAMETER\":\"some value1\"}," +
                        "\"runtime\":{\"NEW_NS_RUNTIME_PARAMETER\":\"some value2\"}," +
                        "\"pipeline\":{\"NEW_NS_PIPELINE_PARAMETER\":\"some value3\"}" +
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
                        "\"deployment\":{\"NEW_NS_DEPLOY_PARAMETER\":\"some value1\"}," +
                        "\"runtime\":{\"NEW_NS_RUNTIME_PARAMETER\":\"some value2\"}" +
                        "}}")
                .when().post("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns&applicationName=my-app")
                .then()
                .statusCode(204);


        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns&applicationName=my-app")
                .then()
                .statusCode(200)
                .body("parameters.deployment.NEW_NS_DEPLOY_PARAMETER", equalTo("some value1"))
                .body("parameters.runtime.NEW_NS_RUNTIME_PARAMETER", equalTo("some value2"))
                .body("parameters.pipeline", anEmptyMap());
    }

    @Test
    @TestSecurity(user = "test")
    void set_ui_parameters_application_level_runtime_parameter() throws Exception {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .contentType("application/json")
                .body("{\"commitInfo\": {\"username\": \"test\", \"email\": \"test@mail.com\", \"commitMessage\": \"test\"}," +
                        "\"parameters\": {" +
                        "\"runtime\":{\"MY_APP_RUNTIME_PARAMETER\":\"barRestUpdated\"}" +
                        "}}")
                .when().post("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns&applicationName=my-app")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns&applicationName=my-app")
                .then()
                .statusCode(200)
                .body("parameters.runtime.MY_APP_RUNTIME_PARAMETER", equalTo("barRestUpdated"));

        Cluster cluster = clusterRepository.listAll().stream()
                .filter(c -> c.getName().equals("test-cluster"))
                .findFirst().orElseThrow();
        File envDefFile = new File(cluster.getGitInfo().folderName() + "/environments/test-cluster/env-metadata-test/Inventory/env_definition.yml");
        System.out.println("=== env_definition.yml after parameter update ===\n" + FileUtils.readFileToString(envDefFile, "UTF-8"));
    }

    @Test
    @TestSecurity(user = "test")
    void set_ui_parameters_application_level_pipeline_context_is_not_allowed() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .contentType("application/json")
                .body("{\"commitInfo\": {\"username\": \"test\", \"email\": \"test@mail.com\", \"commitMessage\": \"test\"}," +
                        "\"parameters\": {" +
                        "\"deployment\":{\"NEW_NS_DEPLOY_PARAMETER\":\"some value1\"}," +
                        "\"runtime\":{\"NEW_NS_RUNTIME_PARAMETER\":\"some value2\"}," +
                        "\"pipeline\":{\"NEW_NS_PIPELINE_PARAMETER\":\"some value3\"}" +
                        "}}")
                .when().post("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns&applicationName=my-app")
                .then()
                .statusCode(400);
    }

    @Test
    @TestSecurity(user = "test")
    void set_ui_parameters_namespace_level_pipeline_context_is_not_allowed() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .contentType("application/json")
                .body("{\"commitInfo\": {\"username\": \"test\", \"email\": \"test@mail.com\", \"commitMessage\": \"test\"}," +
                        "\"parameters\": {" +
                        "\"deployment\":{\"NEW_NS_DEPLOY_PARAMETER\":\"some value1\"}," +
                        "\"runtime\":{\"NEW_NS_RUNTIME_PARAMETER\":\"some value2\"}," +
                        "\"pipeline\":{\"NEW_NS_PIPELINE_PARAMETER\":\"some value3\"}" +
                        "}}")
                .when().post("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns")
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
                        "\"deployment\":{}," +
                        "\"runtime\":{}" +
                        "}}")
                .when().post("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/ui-parameters?namespaceName=test-ns")
                .then()
                .statusCode(200)
                .body("parameters.deployment", anEmptyMap())
                .body("parameters.runtime", anEmptyMap());
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
            FileUtils.deleteDirectory(new File(dest, "environments/test-cluster/env-metadata-test"));
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
    @TestSecurity(user = "test")
    void sync_removes_deleted_cluster_from_cache() {
        // First sync: test-cluster (solar_earth) and unreachable-cluster (solar_saturn) are loaded
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

        // Second sync: remove test-cluster folder from git, but keep solar_earth project intact.
        // removeDeletedProjects() will not trigger because the project still exists —
        // only the cluster itself is gone. Bug: saveDataToCache() is never called for the removed
        // cluster, so nothing evicts it from Redis.
        mockGitService.setCloneAction((url, dest) -> {
            FileUtils.copyDirectory(new File("src/test/resources/" + url), dest);
            FileUtils.deleteDirectory(new File(dest, "environments/test-cluster"));
        });
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        // test-cluster and its environments should be removed; unreachable-cluster and env-1 must remain
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
    @TestSecurity(user = "test")
    void get_applications_returns_filtered_list() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/applications?namespaceName=test-ns")
                .then()
                .statusCode(200)
                .body(".", containsInAnyOrder("MONITORING", "postgres", "postgres-services", "my-app"));
    }

    @Test
    @TestSecurity(user = "test")
    void get_applications_no_matching_deploy_postfix() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/applications?namespaceName=test-bss")
                .then()
                .statusCode(200)
                .body(".", empty());
    }

    @Test
    @TestSecurity(user = "test")
    void get_applications_namespace_not_found() {
        Environment environment = prepareEnvironmentForTests("env-metadata-test");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/applications?namespaceName=non-existent-ns")
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "test")
    void get_applications_no_sd_file_returns_empty() {
        Environment environment = prepareEnvironmentForTests("env-1");

        given()
                .when().get("/colly/v2/inventory-service/environments/" + environment.getId() + "/applications?namespaceName=namespace-1")
                .then()
                .statusCode(200)
                .body(".", empty());
    }

    @Test
    @TestSecurity(user = "test")
    void get_applications_environment_not_found() {
        given()
                .when().post("/colly/v2/inventory-service/manual-sync")
                .then()
                .statusCode(204);

        given()
                .when().get("/colly/v2/inventory-service/environments/non-existent-id/applications?namespaceName=test-ns")
                .then()
                .statusCode(404);
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

    private void writeMinimalEnvironment(File envDir, String description) throws IOException {
        File inventoryDir = new File(envDir, "Inventory");
        FileUtils.forceMkdir(inventoryDir);
        FileUtils.writeStringToFile(new File(inventoryDir, "env_definition.yml"), """
                inventory:
                  environmentName: "%s"
                  description: "%s"
                """.formatted(envDir.getName(), description), "UTF-8");
    }

    private void writeMinimalClusterCloudPassport(File clusterDir, String region, String token) throws IOException {
        File cloudPassportDir = new File(clusterDir, "cloud-passport");
        FileUtils.forceMkdir(cloudPassportDir);
        FileUtils.writeStringToFile(new File(cloudPassportDir, "passport.yml"), """
                ---
                version: 1.5
                cloud:
                  CLOUD_API_HOST: some-host.example.com
                  CLOUD_API_PORT: "443"
                  CLOUD_DEPLOY_TOKEN: cloud-deploy-sa-token
                  CLOUD_PUBLIC_HOST: some-host.example.com
                  CLOUD_PROTOCOL: https
                  REGION: %s
                """.formatted(region), "UTF-8");
        FileUtils.writeStringToFile(new File(cloudPassportDir, "passport-creds.yml"), """
                ---
                cloud-deploy-sa-token:
                  type: "secret"
                  data:
                    secret: "%s"
                """.formatted(token), "UTF-8");
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

