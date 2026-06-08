package org.qubership;

import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.colly.GitService;
import org.qubership.colly.db.EnvironmentRepository;
import org.qubership.colly.db.data.Environment;

import java.io.File;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestTransaction
@TestSecurity(user = "test")
class EffectiveSetRestTest {

    private static final String BASE = "/colly/v2/inventory-service";
    private static final String SYNC = BASE + "/manual-sync";
    private static final String ES = BASE + "/environments/{id}/effective-set";

    private static final String NS_CORE = "test-ns";
    private static final String APP = "application-2";
    private static final String NS_UNKNOWN = "no-such-namespace";

    @InjectMock
    GitService gitService;

    @Inject
    EnvironmentRepository environmentRepository;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.doAnswer(inv -> {
            FileUtils.copyDirectory(
                    new File("src/test/resources/" + inv.getArgument(0)),
                    inv.getArgument(3));
            return null;
        }).when(gitService).cloneRepository(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private String syncAndGetEnvId(String envName) {
        given().when().post(SYNC).then().statusCode(204);
        return environmentRepository.listAll().stream()
                .filter(e -> e.getName().equals(envName))
                .map(Environment::getId)
                .findFirst()
                .orElseThrow();
    }

    // ── deployment happy-path ────────────────────────────────────────────────

    @Test
    void deployment_returnsWrappedParams() {
        String id = syncAndGetEnvId("env-metadata-test");
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .queryParam("context", "deployment")
                .queryParam("namespaceName", NS_CORE)
                .queryParam("applicationName", APP)
                .when().post(ES, id)
                .then().statusCode(200)
                .body("context", equalTo("deployment"))
                .body("namespaceName", equalTo(NS_CORE))
                .body("applicationName", equalTo(APP))
                .body("parameters.PARAMETER_1._type", equalTo("leaf"))
                .body("parameters.PARAMETER_1._data.value", equalTo("xbmfqlzrtk"))
                .body("parameters.PARAMETER_1._data.state", equalTo("ui_override_untouched"))
                .body("parameters.PARAMETER_1._data.originalValue", equalTo("xbmfqlzrtk"));
    }

    @Test
    void deployment_global_key_excluded() {
        String id = syncAndGetEnvId("env-metadata-test");
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .queryParam("context", "deployment")
                .queryParam("namespaceName", NS_CORE)
                .queryParam("applicationName", APP)
                .when().post(ES, id)
                .then().statusCode(200)
                .body("parameters", not(hasKey("global")));
    }

    @Test
    void deployment_mergesRequestBody() {
        String id = syncAndGetEnvId("env-metadata-test");
        given()
                .contentType(ContentType.JSON)
                .body("{\"parameters\":{\"PARAMETER_1\":\"overridden\",\"NEW_KEY\":\"new-value\"}}")
                .queryParam("context", "deployment")
                .queryParam("namespaceName", NS_CORE)
                .queryParam("applicationName", APP)
                .when().post(ES, id)
                .then().statusCode(200)
                .body("parameters.PARAMETER_1._data.value", equalTo("overridden"))
                .body("parameters.NEW_KEY._data.value", equalTo("new-value"));
    }

    @Test
    void deployment_mergesRequestBody_null_is_literal() {
        String id = syncAndGetEnvId("env-metadata-test");
        given()
                .contentType(ContentType.JSON)
                .body("{\"parameters\":{\"PARAMETER_1\":null}}")
                .queryParam("context", "deployment")
                .queryParam("namespaceName", NS_CORE)
                .queryParam("applicationName", APP)
                .when().post(ES, id)
                .then().statusCode(200)
                .body("parameters", hasKey("PARAMETER_1"))
                .body("parameters.PARAMETER_1._data.value", nullValue());
    }

    // ── runtime ─────────────────────────────────────────────────────────────

    @Test
    void runtime_returnsEmptyWhenNoData() {
        String id = syncAndGetEnvId("env-metadata-test");
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .queryParam("context", "runtime")
                .queryParam("namespaceName", NS_CORE)
                .queryParam("applicationName", APP)
                .when().post(ES, id)
                .then().statusCode(200)
                .body("context", equalTo("runtime"))
                .body("parameters", anEmptyMap());
    }

    // ── pipeline ─────────────────────────────────────────────────────────────

    @Test
    void pipeline_returnsWrappedParams() {
        String id = syncAndGetEnvId("env-metadata-test");
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .queryParam("context", "pipeline")
                .when().post(ES, id)
                .then().statusCode(200)
                .body("context", equalTo("pipeline"))
                .body("namespaceName", nullValue())
                .body("applicationName", nullValue())
                .body("parameters", not(anEmptyMap()));
    }

    @Test
    void noEffectiveSetFiles_returnsEmpty() {
        String id = syncAndGetEnvId("env-test");
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .queryParam("context", "pipeline")
                .when().post(ES, id)
                .then().statusCode(200)
                .body("parameters", anEmptyMap());
    }

    // ── 400 validation ───────────────────────────────────────────────────────

    @Test
    void invalidContext_returns400() {
        String id = syncAndGetEnvId("env-metadata-test");
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .queryParam("context", "unknown")
                .when().post(ES, id)
                .then().statusCode(400);
    }

    @Test
    void pipeline_withNamespace_returns400() {
        String id = syncAndGetEnvId("env-metadata-test");
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .queryParam("context", "pipeline")
                .queryParam("namespaceName", NS_CORE)
                .when().post(ES, id)
                .then().statusCode(400);
    }

    @Test
    void pipeline_withApplication_returns400() {
        String id = syncAndGetEnvId("env-metadata-test");
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .queryParam("context", "pipeline")
                .queryParam("applicationName", APP)
                .when().post(ES, id)
                .then().statusCode(400);
    }

    @Test
    void missingNamespace_returns400() {
        String id = syncAndGetEnvId("env-metadata-test");
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .queryParam("context", "deployment")
                .queryParam("applicationName", APP)
                .when().post(ES, id)
                .then().statusCode(400);
    }

    @Test
    void missingApplication_returns400() {
        String id = syncAndGetEnvId("env-metadata-test");
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .queryParam("context", "deployment")
                .queryParam("namespaceName", NS_CORE)
                .when().post(ES, id)
                .then().statusCode(400);
    }

    // ── 404 ──────────────────────────────────────────────────────────────────

    @Test
    void environmentNotFound_returns404() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .queryParam("context", "deployment")
                .queryParam("namespaceName", NS_CORE)
                .queryParam("applicationName", APP)
                .when().post(ES, UUID.randomUUID().toString())
                .then().statusCode(404);
    }

    @Test
    void namespaceNotFound_returns404() {
        String id = syncAndGetEnvId("env-metadata-test");
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .queryParam("context", "deployment")
                .queryParam("namespaceName", NS_UNKNOWN)
                .queryParam("applicationName", APP)
                .when().post(ES, id)
                .then().statusCode(404);
    }

    @Test
    void applicationNotFound_returns404() {
        String id = syncAndGetEnvId("env-metadata-test");
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .queryParam("context", "deployment")
                .queryParam("namespaceName", NS_CORE)
                .queryParam("applicationName", "no-such-app")
                .when().post(ES, id)
                .then().statusCode(404);
    }
}
