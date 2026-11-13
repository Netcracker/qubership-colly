package org.qubership.colly;

import com.netcracker.cloud.security.common.token.m2m.cache.UrlCache;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Proves the M2M wiring in the real client stack (PDPLDEVOPS-25309): the
 * {@code @RegisterProvider(M2mRequestFilter.class)} on EnvgeneInventoryServiceRest
 * is honored by the MP REST client, and in hybrid/k8s modes the Authorization
 * header actually carries the projected SA token read from the (fake) tokens
 * directory, while legacy mode never uses it. The inventory-service is faked
 * with a JDK HttpServer; the keycloak path uses the OIDC dev service, like in
 * the other @QuarkusTest classes.
 */
@QuarkusTest
@TestProfile(M2mQuarkusTest.Profile.class)
class M2mQuarkusTest {

    private static final int PORT = 18099;
    private static final Path TOKENS_DIR = Path.of("target", "test-m2m-tokens");
    private static final String K8S_TOKEN = "k8s-qa-token";

    // Must be set before the first use of KubernetesAudienceToken: its token
    // source resolves the tokens directory on first access and then caches.
    static {
        try {
            Files.createDirectories(TOKENS_DIR.resolve("netcracker"));
            Files.writeString(TOKENS_DIR.resolve("netcracker").resolve("token"), K8S_TOKEN);
            System.setProperty("com.netcracker.cloud.security.kubernetes.tokens.dir", TOKENS_DIR.toString());
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static HttpServer server;
    private static volatile String lastAuthorization;
    // Remaining requests to answer with 401, consumed one by one (0 = always 200).
    private static volatile int pending401s;

    @BeforeAll
    static void startFakeInventory() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        server.createContext("/colly/v2/inventory-service/internal/cluster-infos", exchange -> {
            lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (pending401s > 0) {
                pending401s--;
                exchange.sendResponseHeaders(401, -1);
                exchange.close();
                return;
            }
            byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stopFakeInventory() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Inject
    @RestClient
    EnvgeneInventoryServiceRest inventoryServiceRest;

    @Inject
    CollyStorage collyStorage;

    @Test
    void hybrid_mode_sends_projected_kubernetes_token() {
        assumeTrue(System.getenv("M2M_AUTH_MODE") == null);
        System.setProperty("M2M_AUTH_MODE", "hybrid");
        try {
            inventoryServiceRest.getClusterInfos();

            assertEquals("Bearer " + K8S_TOKEN, lastAuthorization);
        } finally {
            System.clearProperty("M2M_AUTH_MODE");
        }
    }

    @Test
    void k8s_mode_sends_projected_kubernetes_token() {
        assumeTrue(System.getenv("M2M_AUTH_MODE") == null);
        System.setProperty("M2M_AUTH_MODE", "k8s");
        try {
            inventoryServiceRest.getClusterInfos();

            assertEquals("Bearer " + K8S_TOKEN, lastAuthorization);
        } finally {
            System.clearProperty("M2M_AUTH_MODE");
        }
    }

    @Test
    void hybrid_401_retry_success_caches_the_keycloak_peer() {
        assumeTrue(System.getenv("M2M_AUTH_MODE") == null);
        System.setProperty("M2M_AUTH_MODE", "hybrid");
        pending401s = 1;
        try {
            // k8s token -> 401 -> retry with the keycloak token -> 200.
            // Via CollyStorage: the 401 retry wrapper lives there, not in the client.
            collyStorage.fetchClusterInfos();

            // The response half ran on a Vert.x callback thread, not this one: the
            // cache store must have survived the thread hop via the request property.
            URI inventoryUri = URI.create("http://127.0.0.1:" + PORT + "/colly/v2/inventory-service/internal/cluster-infos");
            assertTrue(M2mTokenRoute.cache().containsKey(UrlCache.calculateCacheKey(inventoryUri)),
                    "the keycloak route must be cached after a successful 401 retry");
            assertFalse(("Bearer " + K8S_TOKEN).equals(lastAuthorization));
        } finally {
            System.clearProperty("M2M_AUTH_MODE");
            pending401s = 0;
            M2mTokenRoute.cache().invalidateAll();
        }
    }

    @Test
    void legacy_mode_does_not_use_kubernetes_token() {
        assumeTrue(System.getenv("M2M_AUTH_MODE") == null);
        System.setProperty("M2M_AUTH_MODE", "legacy");
        try {
            inventoryServiceRest.getClusterInfos();

            // The dev Keycloak serves a real keycloak token (or the fetch fails and
            // no header goes out); either way the projected k8s token must never be
            // used in legacy mode.
            assertFalse(("Bearer " + K8S_TOKEN).equals(lastAuthorization));
        } finally {
            System.clearProperty("M2M_AUTH_MODE");
        }
    }

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // The OIDC dev service (Keycloak) serves the keycloak-token path exactly
            // like in the other @QuarkusTest classes; the k8s-token path does not
            // touch the IdP at all.
            return Map.of(
                    "quarkus.rest-client.envgene-inventory-service.url", "http://127.0.0.1:" + PORT);
        }
    }
}
