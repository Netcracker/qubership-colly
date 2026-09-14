package org.qubership;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.colly.MockGitService;
import org.qubership.colly.db.EnvironmentRepository;
import org.qubership.colly.db.data.Environment;

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
    private static final String APP = "my-app";
    private static final String NS_UNKNOWN = "no-such-namespace";

    @Inject
    MockGitService gitService;
    @Inject
    EnvironmentRepository environmentRepository;

    @BeforeEach
    void setUp() {
        gitService.reset();
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
    void deployment_yaml_alias_keys_excluded() {
        // deployment-parameters.yaml has `global: &id001 {...}` followed by
        // `service-1: *id001` ... `service-8: *id001`.
        // filterGlobalAliases must exclude all alias keys, not just "global" itself.
        String id = syncAndGetEnvId("env-metadata-test");
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .queryParam("context", "deployment")
                .queryParam("namespaceName", NS_CORE)
                .queryParam("applicationName", APP)
                .when().post(ES, id)
                .then().statusCode(200)
                .body("parameters", not(hasKey("service-1")))
                .body("parameters", not(hasKey("service-2")))
                .body("parameters", not(hasKey("service-3")))
                .body("parameters", not(hasKey("service-4")))
                .body("parameters", not(hasKey("service-5")))
                .body("parameters", not(hasKey("service-6")))
                .body("parameters", not(hasKey("service-7")))
                .body("parameters", not(hasKey("service-8")))
                .body("parameters", hasKey("PARAMETER_1")); // flat keys must still be present
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
                .body("parameters.PARAMETER_1._data.state", equalTo("ui_override_uncommitted"))
                .body("parameters.PARAMETER_1._data.originalValue", equalTo("xbmfqlzrtk"))
                .body("parameters.NEW_KEY._data.value", equalTo("new-value"))
                .body("parameters.NEW_KEY._data.state", equalTo("ui_override_uncommitted"))
                .body("parameters.NEW_KEY._data.originalValue", nullValue());
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
                .body("parameters.PARAMETER_1._data.value", nullValue())
                .body("parameters.PARAMETER_1._data.state", equalTo("ui_override_uncommitted"))
                .body("parameters.PARAMETER_1._data.originalValue", equalTo("xbmfqlzrtk"));
    }

    // ── deployment: applicable paramsets merged in ──────────────────────────

    @Test
    void deployment_mergesEnvironmentAndNamespaceLevelParamsets() {
        // ENV_DEPLOY_PARAMETER comes from the "cloud" (ENVIRONMENT-level) deploy-ui-override paramset.
        // CORE_DEPLOY_PARAMETER(_2) comes from the "core" (NAMESPACE-level) core-deploy-ui-override paramset.
        String id = syncAndGetEnvId("env-metadata-test");
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .queryParam("context", "deployment")
                .queryParam("namespaceName", NS_CORE)
                .queryParam("applicationName", APP)
                .when().post(ES, id)
                .then().statusCode(200)
                .body("parameters.ENV_DEPLOY_PARAMETER._data.value", equalTo("some value"))
                .body("parameters.ENV_DEPLOY_PARAMETER._data.state", equalTo("ui_override_committed"))
                .body("parameters.ENV_DEPLOY_PARAMETER._data.originalValue", nullValue())
                .body("parameters.MY_APP_DEPLOY_PARAMETER._data.value", equalTo("foo"))
                .body("parameters.CORE_MY_APP_CLUSTER_PARAM._data.value", equalTo("cluster level value"))
                .body("parameters.CORE_DEPLOY_PARAMETER._data.value", equalTo("some value"))
                .body("parameters.CORE_DEPLOY_PARAMETER._data.state", equalTo("ui_override_committed"))
                .body("parameters.CORE_DEPLOY_PARAMETER._data.originalValue", nullValue())
                .body("parameters.CORE_DEPLOY_PARAMETER_2._type", equalTo("container"))
                .body("parameters.CORE_DEPLOY_PARAMETER_2._data.SECOND_LEVEL_KEY._data.value", equalTo("some value"))
                .body("parameters.CORE_DEPLOY_PARAMETER_2._data.SECOND_LEVEL_KEY._data.state", equalTo("ui_override_committed"))
                // file-based data must still be present alongside the merged paramsets
                .body("parameters.PARAMETER_1._data.value", equalTo("xbmfqlzrtk"))
                .body("parameters.PARAMETER_1._data.state", equalTo("ui_override_untouched"));
    }

    @Test
    void deployment_applicationLevelParamsetAppliesToMatchingApplication() {
        // core-mixed-paramset defines GENERIC_NAMESPACE_PARAM at NAMESPACE level (applies to any app in "core")
        // and GENERIC_APP_PARAM / MY_APP_DEPLOY_PARAMETER at APPLICATION level for appName "my-app" — since
        // APP == "my-app", both must be present.
        // core-my-second-app-deploy-ui-override defines the SAME key MY_APP_DEPLOY_PARAMETER ("bar2") but scoped
        // to a different application ("my-second-app") — that value must not win; "my-app"'s own value ("foo") must.
        String id = syncAndGetEnvId("env-metadata-test");
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .queryParam("context", "deployment")
                .queryParam("namespaceName", NS_CORE)
                .queryParam("applicationName", APP)
                .when().post(ES, id)
                .then().statusCode(200)
                .body("parameters.GENERIC_NAMESPACE_PARAM._data.value", equalTo("namespace value"))
                .body("parameters.GENERIC_NAMESPACE_PARAM._data.state", equalTo("ui_override_committed"))
                .body("parameters.GENERIC_APP_PARAM._data.value", equalTo("app value"))
                .body("parameters.GENERIC_APP_PARAM._data.state", equalTo("ui_override_committed"))
                .body("parameters.MY_APP_DEPLOY_PARAMETER._data.value", equalTo("foo"));
    }

    @Test
    void deployment_applicationLevelParamsetOverridesNamespaceLevelOnSameKey() {
        // mixed-paramset-same-parameter sets PARAM at both NAMESPACE level ("namespace value") and, for
        // appName "my-app", at APPLICATION level ("app value") — the more specific APPLICATION-level value
        // must win the merge.
        String id = syncAndGetEnvId("env-metadata-test");
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .queryParam("context", "deployment")
                .queryParam("namespaceName", NS_CORE)
                .queryParam("applicationName", APP)
                .when().post(ES, id)
                .then().statusCode(200)
                .body("parameters.PARAM._data.value", equalTo("app value"));
    }

    @Test
    void deployment_paramsetValue_canStillBeOverriddenByRequestBody() {
        // Uncommitted request-body parameters must win over the persisted paramset value —
        // paramsets are part of "the Effective Set", the request body overlays on top of it.
        String id = syncAndGetEnvId("env-metadata-test");
        given()
                .contentType(ContentType.JSON)
                .body("{\"parameters\":{\"CORE_DEPLOY_PARAMETER\":\"overridden-by-request\"}}")
                .queryParam("context", "deployment")
                .queryParam("namespaceName", NS_CORE)
                .queryParam("applicationName", APP)
                .when().post(ES, id)
                .then().statusCode(200)
                .body("parameters.CORE_DEPLOY_PARAMETER._data.value", equalTo("overridden-by-request"))
                .body("parameters.CORE_DEPLOY_PARAMETER._data.state", equalTo("ui_override_uncommitted"))
                .body("parameters.CORE_DEPLOY_PARAMETER._data.originalValue", equalTo("some value"));
    }

    @Test
    void deployment_onKeyCollisionBetweenParamsets_laterOneInEnvDefinitionWins() {
        // DUPLICATE_PARAM is set by both core-first-param ("first value") and core-second-param
        // ("second value"); core-second-param is listed after core-first-param under envSpecificParamsets.core
        // in env_definition.yml, so it must win.
        String id = syncAndGetEnvId("env-metadata-test");
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .queryParam("context", "deployment")
                .queryParam("namespaceName", NS_CORE)
                .queryParam("applicationName", APP)
                .when().post(ES, id)
                .then().statusCode(200)
                .body("parameters.DUPLICATE_PARAM._data.value", equalTo("second value"));
    }

    // ── runtime ─────────────────────────────────────────────────────────────

    @Test
    void runtime_returnsApplicableParamsets_whenNoEffectiveSetFile() {
        // No runtime/effective-set file exists for NS_CORE/APP, but the environment-, namespace- and
        // (since APP == "my-app") application-level runtime paramsets configured in env_definition.yml still apply.
        // MY_APP_RUNTIME_PARAMETER is set by both core-my-app-runtime-ui-override ("bar") and
        // core-my-app-runtime-manual-params ("barManual"); the latter is listed later under
        // envSpecificTechnicalParamsets.core, so it must win.
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
                .body("parameters.ENV_RUNTIME_PARAMETER._data.value", equalTo("some value"))
                .body("parameters.CORE_RUNTIME_PARAMETER._data.value", equalTo("some value3"))
                .body("parameters.MY_APP_RUNTIME_PARAMETER._data.value", equalTo("barManual"));
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
    void pipeline_mergesEnvironmentLevelParamsetOnly() {
        // ENV_PIPELINE_PARAMETER ("cloud" / ENVIRONMENT-level) applies to pipeline context, but
        // CORE_PIPELINE_PARAMETER ("core" / NAMESPACE-level) must not — pipeline has no namespace.
        String id = syncAndGetEnvId("env-metadata-test");
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .queryParam("context", "pipeline")
                .when().post(ES, id)
                .then().statusCode(200)
                .body("parameters.ENV_PIPELINE_PARAMETER._data.value", equalTo("some value"))
                .body("parameters", not(hasKey("CORE_PIPELINE_PARAMETER")));
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
