package org.qubership.colly;

import com.netcracker.cloud.security.common.token.m2m.cache.UrlCache;
import com.netcracker.cloud.security.core.utils.k8s.AudienceName;
import com.netcracker.cloud.security.core.utils.k8s.KubernetesAudienceToken;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit test for the M2M token selection (request half of the Fallback Algorithm,
 * docs/design/m2m-kubernetes/0.transition-plan.md section 0.2). M2M_AUTH_MODE is
 * driven through a system property; the kubernetes token through a static mock;
 * the keycloak token through the supplier test seam (no CDI/OIDC in unit scope).
 */
class M2mRequestFilterTest {

    private static final String M2M_AUTH_MODE = "M2M_AUTH_MODE";
    private static final String AUTH = "Authorization";
    private static final URI INVENTORY_URI =
            URI.create("http://envgene-inventory-service/colly/v2/inventory-service/internal/cluster-infos");

    private ClientRequestContext requestContext;
    private MultivaluedMap<String, Object> headers;
    private AtomicInteger keycloakLookups;
    private M2mRequestFilter filter;
    // A bare mock would no-op setProperty/getProperty; the filter hands the
    // keycloak decision to the response half via the request properties.
    private final Map<String, Object> requestProperties = new HashMap<>();

    @BeforeEach
    void setUp() {
        assumeTrue(System.getenv(M2M_AUTH_MODE) == null,
                "M2M_AUTH_MODE is set in the environment; the system-property driven test would be unreliable");
        keycloakLookups = new AtomicInteger();
        // supplier seam: "kc-token" is the keycloak token the service-client would fetch
        filter = new M2mRequestFilter(() -> keycloakLookups.incrementAndGet() == 1 ? "kc-token" : null);
        requestContext = mock(ClientRequestContext.class);
        headers = new MultivaluedHashMap<>();
        when(requestContext.getHeaders()).thenReturn(headers);
        when(requestContext.getUri()).thenReturn(INVENTORY_URI);
        doAnswer(invocation -> {
            requestProperties.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(requestContext).setProperty(anyString(), any());
        when(requestContext.getProperty(anyString()))
                .thenAnswer(invocation -> requestProperties.get(invocation.getArgument(0)));
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(M2M_AUTH_MODE);
        M2mTokenRoute.endRequest();
        M2mTokenRoute.cache().invalidateAll();
    }

    @Test
    void user_token_request_is_left_untouched() {
        headers.putSingle(AUTH, "Bearer user-token");
        M2mTokenRoute.markUserToken();

        try (MockedStatic<KubernetesAudienceToken> k8s = mockStatic(KubernetesAudienceToken.class)) {
            filter.filter(requestContext);

            assertEquals("Bearer user-token", headers.getFirst(AUTH));
            assertEquals(M2mTokenRoute.Route.USER_TOKEN, M2mTokenRoute.route());
            k8s.verifyNoInteractions();
            assertEquals(0, keycloakLookups.get());
        }
    }

    @Test
    void legacy_mode_uses_keycloak_token() {
        filter.filter(requestContext);

        assertEquals("Bearer kc-token", headers.getFirst(AUTH));
        assertEquals(M2mTokenRoute.Route.KEYCLOAK, M2mTokenRoute.route());
    }

    @Test
    void legacy_mode_without_keycloak_token_sends_no_authorization() {
        // No IdP reachable: the request goes out without an Authorization header,
        // same as the pre-migration behavior on token-fetch failure.
        filter = new M2mRequestFilter(() -> null);
        filter.filter(requestContext);

        assertNull(headers.getFirst(AUTH));
        assertEquals(M2mTokenRoute.Route.KEYCLOAK, M2mTokenRoute.route());
    }

    @Test
    void hybrid_mode_uses_kubernetes_token_when_available() {
        System.setProperty(M2M_AUTH_MODE, "hybrid");

        try (MockedStatic<KubernetesAudienceToken> k8s = mockStatic(KubernetesAudienceToken.class)) {
            k8s.when(() -> KubernetesAudienceToken.getToken(AudienceName.NETCRACKER)).thenReturn("k8s-token");

            filter.filter(requestContext);

            assertEquals("Bearer k8s-token", headers.getFirst(AUTH));
            assertEquals(M2mTokenRoute.Route.KUBERNETES, M2mTokenRoute.route());
            k8s.verify(() -> KubernetesAudienceToken.getToken(AudienceName.NETCRACKER));
            assertEquals(0, keycloakLookups.get(), "keycloak must not be consulted when the k8s token is available");
        }
    }

    @Test
    void k8s_mode_uses_kubernetes_token_when_available() {
        System.setProperty(M2M_AUTH_MODE, "k8s");

        try (MockedStatic<KubernetesAudienceToken> k8s = mockStatic(KubernetesAudienceToken.class)) {
            k8s.when(() -> KubernetesAudienceToken.getToken(AudienceName.NETCRACKER)).thenReturn("k8s-token");

            filter.filter(requestContext);

            assertEquals("Bearer k8s-token", headers.getFirst(AUTH));
            assertEquals(M2mTokenRoute.Route.KUBERNETES, M2mTokenRoute.route());
            assertEquals(0, keycloakLookups.get());
        }
    }

    @Test
    void hybrid_mode_falls_back_to_keycloak_when_kubernetes_token_unavailable() {
        System.setProperty(M2M_AUTH_MODE, "hybrid");

        try (MockedStatic<KubernetesAudienceToken> k8s = mockStatic(KubernetesAudienceToken.class)) {
            k8s.when(() -> KubernetesAudienceToken.getToken(AudienceName.NETCRACKER))
                    .thenThrow(new IllegalArgumentException("no projected token volume"));

            filter.filter(requestContext);

            assertEquals("Bearer kc-token", headers.getFirst(AUTH));
            assertEquals(M2mTokenRoute.Route.KEYCLOAK, M2mTokenRoute.route());
            assertEquals(M2mTokenRoute.KeycloakReason.ACQUISITION_FALLBACK, M2mTokenRoute.keycloakReason());
            assertEquals(1, keycloakLookups.get());
        }
    }

    @Test
    void k8s_mode_does_not_fall_back_when_kubernetes_token_unavailable() {
        System.setProperty(M2M_AUTH_MODE, "k8s");

        try (MockedStatic<KubernetesAudienceToken> k8s = mockStatic(KubernetesAudienceToken.class)) {
            k8s.when(() -> KubernetesAudienceToken.getToken(AudienceName.NETCRACKER))
                    .thenThrow(new IllegalArgumentException("no projected token volume"));

            filter.filter(requestContext);

            assertNull(headers.getFirst(AUTH));
            assertEquals(M2mTokenRoute.Route.KUBERNETES, M2mTokenRoute.route());
            assertEquals(0, keycloakLookups.get(), "k8s mode must never fall back to keycloak");
        }
    }

    @Test
    void cached_keycloak_peer_skips_kubernetes_attempt() {
        System.setProperty(M2M_AUTH_MODE, "hybrid");
        M2mTokenRoute.beginRequest(INVENTORY_URI);
        M2mTokenRoute.cache().store(M2mTokenRoute.cachedKey());
        M2mTokenRoute.endRequest();

        try (MockedStatic<KubernetesAudienceToken> k8s = mockStatic(KubernetesAudienceToken.class)) {
            filter.filter(requestContext);

            assertEquals("Bearer kc-token", headers.getFirst(AUTH));
            assertEquals(M2mTokenRoute.Route.KEYCLOAK, M2mTokenRoute.route());
            k8s.verifyNoInteractions();
        }
    }

    @Test
    void forced_keycloak_retry_ignores_available_kubernetes_token() {
        System.setProperty(M2M_AUTH_MODE, "hybrid");
        M2mTokenRoute.forceKeycloakOnce();

        try (MockedStatic<KubernetesAudienceToken> k8s = mockStatic(KubernetesAudienceToken.class)) {
            filter.filter(requestContext);

            assertEquals("Bearer kc-token", headers.getFirst(AUTH));
            assertEquals(M2mTokenRoute.Route.KEYCLOAK, M2mTokenRoute.route());
            assertEquals(M2mTokenRoute.KeycloakReason.UNAUTHORIZED_RETRY, M2mTokenRoute.keycloakReason());
            k8s.verifyNoInteractions();
        }
    }

    @Test
    void cached_key_is_computed_from_request_uri() {
        filter.filter(requestContext);

        String key = M2mTokenRoute.cachedKey();
        assertFalse(key == null || key.isEmpty());
        assertTrue(key.contains("envgene-inventory-service"), "cache key must be host-based, got: " + key);
    }

    @Test
    void legacy_mode_never_touches_the_kubernetes_token_api() {
        System.setProperty(M2M_AUTH_MODE, "legacy");

        try (MockedStatic<KubernetesAudienceToken> k8s = mockStatic(KubernetesAudienceToken.class)) {
            filter.filter(requestContext);

            assertEquals("Bearer kc-token", headers.getFirst(AUTH));
            assertEquals(1, keycloakLookups.get());
            k8s.verifyNoInteractions();
        }
    }

    // ---- response half: the per-URL decision cache ----
    // The response half reads its decision from the request property the request
    // half set (in production it runs on the Vert.x callback thread), so these
    // tests run the real request half first instead of simulating the route.

    private static String keyOf() {
        return UrlCache.calculateCacheKey(INVENTORY_URI);
    }

    private ClientResponseContext response(int status) {
        ClientResponseContext responseContext = mock(ClientResponseContext.class);
        when(responseContext.getStatus()).thenReturn(status);
        return responseContext;
    }

    @Test
    void hybrid_keycloak_fallback_success_caches_the_peer() {
        System.setProperty(M2M_AUTH_MODE, "hybrid");

        try (MockedStatic<KubernetesAudienceToken> k8s = mockStatic(KubernetesAudienceToken.class)) {
            k8s.when(() -> KubernetesAudienceToken.getToken(AudienceName.NETCRACKER))
                    .thenThrow(new IllegalArgumentException("no projected token volume"));

            filter.filter(requestContext);
            assertEquals("Bearer kc-token", headers.getFirst(AUTH));

            filter.filter(requestContext, response(200));

            assertTrue(M2mTokenRoute.cache().containsKey(keyOf()));
        }
    }

    @Test
    void hybrid_keycloak_success_after_401_retry_caches_the_peer() {
        System.setProperty(M2M_AUTH_MODE, "hybrid");
        M2mTokenRoute.forceKeycloakOnce();

        filter.filter(requestContext);
        assertEquals("Bearer kc-token", headers.getFirst(AUTH));

        filter.filter(requestContext, response(200));

        assertTrue(M2mTokenRoute.cache().containsKey(keyOf()));
    }

    @Test
    void hybrid_kubernetes_success_is_not_cached() {
        // The peer accepted the k8s token: nothing to remember.
        System.setProperty(M2M_AUTH_MODE, "hybrid");

        try (MockedStatic<KubernetesAudienceToken> k8s = mockStatic(KubernetesAudienceToken.class)) {
            k8s.when(() -> KubernetesAudienceToken.getToken(AudienceName.NETCRACKER)).thenReturn("k8s-token");

            filter.filter(requestContext);
            assertEquals("Bearer k8s-token", headers.getFirst(AUTH));

            filter.filter(requestContext, response(200));

            assertFalse(M2mTokenRoute.cache().containsKey(keyOf()));
        }
    }

    @Test
    void hybrid_keycloak_401_is_not_cached() {
        System.setProperty(M2M_AUTH_MODE, "hybrid");
        M2mTokenRoute.forceKeycloakOnce();

        filter.filter(requestContext);

        filter.filter(requestContext, response(401));

        assertFalse(M2mTokenRoute.cache().containsKey(keyOf()));
    }

    @Test
    void legacy_success_is_not_cached() {
        filter.filter(requestContext);
        assertEquals("Bearer kc-token", headers.getFirst(AUTH));

        filter.filter(requestContext, response(200));

        assertFalse(M2mTokenRoute.cache().containsKey(keyOf()));
    }
}
