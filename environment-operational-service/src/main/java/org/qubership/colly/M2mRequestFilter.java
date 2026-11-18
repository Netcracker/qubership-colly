package org.qubership.colly;

import com.netcracker.cloud.security.common.token.m2m.cache.UrlCache;
import com.netcracker.cloud.security.common.util.M2MUtils;
import com.netcracker.cloud.security.core.utils.k8s.AudienceName;
import com.netcracker.cloud.security.core.utils.k8s.KubernetesAudienceToken;
import io.quarkus.logging.Log;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClients;
import io.quarkus.oidc.client.Tokens;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;

import java.util.function.Supplier;

import static com.netcracker.cloud.security.common.constants.M2MConstants.KUBERNETES_TOKEN_ACQUISITION_ERROR;
import static com.netcracker.cloud.security.common.constants.SpecialHeaders.AUTHORIZATION_HEADER;
import static com.netcracker.cloud.security.common.constants.SpecialHeaders.AUTHORIZATION_HEADER_PREFIX;

/**
 * M2M (service-to-service) token handling for the envgene-inventory-service client,
 * the "Fallback Algorithm" from docs/design/m2m-kubernetes/0.transition-plan.md
 * section 0.2 (PDPLDEVOPS-25309).
 * <p>
 * Request half (this filter): pick the token — kubernetes projected token for the
 * netcracker audience in hybrid/k8s modes, keycloak client-credentials token in
 * legacy mode or on fallback; a peer already known to speak keycloak (per-URL
 * decision cache) is not asked for a k8s token again.
 * <p>
 * Response half (this filter): remember a peer that answered 2xx with a keycloak
 * token, so subsequent calls skip the k8s round-trip for 5h (platform UrlCache).
 * This stack runs response filters on the Vert.x callback thread, not the caller
 * thread, so the keycloak decision travels as a request property set by the
 * request half — the same context object is passed to both halves.
 * <p>
 * The 401 retry with the keycloak token lives in {@code CollyStorage#fetchClusterInfos}
 * (a proxy-style retry like the platform's M2MInvocationHandler: a response filter
 * cannot re-invoke the request); the route it needs is kept on the caller thread
 * in {@link M2mTokenRoute}.
 * <p>
 * Hand-written rather than the platform's M2MFilter/M2MManager: those obtain the
 * keycloak token from the hard-coded SpecialRealms.CLOUD_ADMIN_REALM, not from this
 * deployment's idp.url realm — using them as-is would send every token request to
 * the wrong realm. Only the token-reading classes are reused (k8s-utils,
 * security-common); the extension itself is deliberately not on the classpath.
 * <p>
 * A request that already carries an Authorization header (a user token propagated
 * by {@link OidcClientHeadersFactory}) is left untouched, so user calls are never
 * re-authenticated as M2M.
 */
public class M2mRequestFilter implements ClientRequestFilter, ClientResponseFilter {

    /**
     * Carries the keycloak-route decision from the request half to the response
     * half: this stack runs response filters on the Vert.x callback thread, so a
     * ThreadLocal is invisible there, but the request context (and its properties)
     * is the same object in both halves.
     */
    private static final String KEYCLOAK_REASON_PROPERTY = "org.qubership.colly.m2m.keycloakReason";

    private final Supplier<String> keycloakTokenSupplier;

    public M2mRequestFilter() {
        this(M2mRequestFilter::defaultKeycloakToken);
    }

    /** Test seam: keycloak token source instead of the CDI OidcClients lookup. */
    M2mRequestFilter(Supplier<String> keycloakTokenSupplier) {
        this.keycloakTokenSupplier = keycloakTokenSupplier;
    }

    @Override
    public void filter(ClientRequestContext requestContext) {
        MultivaluedMap<String, Object> headers = requestContext.getHeaders();
        if (headers.containsKey(AUTHORIZATION_HEADER)) {
            return;
        }

        M2mTokenRoute.beginRequest(requestContext.getUri());

        if (!M2MUtils.isKubernetesM2MEnabled()) {
            sendKeycloakToken(requestContext, headers, null);
            return;
        }

        if (M2mTokenRoute.isForcedKeycloak()) {
            sendKeycloakToken(requestContext, headers, M2mTokenRoute.KeycloakReason.UNAUTHORIZED_RETRY);
        } else if (M2mTokenRoute.cache().containsKey(M2mTokenRoute.cachedKey())) {
            // Peer was already seen to answer with a keycloak token; skip the k8s round-trip.
            sendKeycloakToken(requestContext, headers, null);
        } else {
            // Route = the token type the request is going out with; marked before the
            // fetch so a failed k8s acquisition in k8s mode (no fallback) still reads
            // KUBERNETES. A hybrid fallback overwrites it with KEYCLOAK below.
            M2mTokenRoute.markKubernetes();
            try {
                String token = KubernetesAudienceToken.getToken(AudienceName.NETCRACKER);
                headers.putSingle(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_PREFIX + token);
            } catch (Exception e) {
                if (!M2MUtils.isKubernetesM2MFallbackEnabled()) {
                    // k8s mode: no fallback — the request goes out without an Authorization
                    // header and any 401 propagates to the caller.
                    Log.errorf(e, "Failed to obtain kubernetes m2m token (M2M_AUTH_MODE=k8s, no fallback)");
                    return;
                }
                Log.debugf(e, "Failed to obtain kubernetes m2m token, falling back to keycloak token");
                sendKeycloakToken(requestContext, headers, M2mTokenRoute.KeycloakReason.ACQUISITION_FALLBACK);
            }
        }
    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) {
        if (!M2MUtils.isKubernetesM2MEnabled()) {
            return;
        }
        // This half runs on the Vert.x callback thread, not the caller thread: the
        // route decision must come from the request property, not M2mTokenRoute.
        M2mTokenRoute.KeycloakReason reason =
                (M2mTokenRoute.KeycloakReason) requestContext.getProperty(KEYCLOAK_REASON_PROPERTY);
        if (reason == null || responseContext.getStatus() / 100 != 2) {
            return;
        }
        String key = UrlCache.calculateCacheKey(requestContext.getUri());
        if (M2mTokenRoute.cache().containsKey(key)) {
            return;
        }
        M2mTokenRoute.cache().store(key);
        if (reason == M2mTokenRoute.KeycloakReason.ACQUISITION_FALLBACK) {
            Log.warnf("Peer answered with a keycloak token after the kubernetes token was unavailable: {} {}",
                    requestContext.getUri(), KUBERNETES_TOKEN_ACQUISITION_ERROR);
        } else {
            Log.debugf("Peer answered with a keycloak token; keycloak route cached for {}", key);
        }
    }

    private void sendKeycloakToken(ClientRequestContext requestContext, MultivaluedMap<String, Object> headers,
                                   M2mTokenRoute.KeycloakReason reason) {
        String token = keycloakTokenSupplier.get();
        if (token != null && !token.isEmpty()) {
            headers.putSingle(AUTHORIZATION_HEADER, AUTHORIZATION_HEADER_PREFIX + token);
            Log.debugf("Propagating service access token via client credentials");
        } else {
            Log.warn("Failed to obtain access token via client credentials");
        }
        M2mTokenRoute.markKeycloak(reason);
        if (reason != null) {
            // Hand the decision to the response half (different thread, same context).
            requestContext.setProperty(KEYCLOAK_REASON_PROPERTY, reason);
        }
    }

    /**
     * Blocking await mirrors the pre-migration pattern of OidcClientHeadersFactory
     * (the client headers are built on the caller thread). On fetch failure the
     * request goes out without an Authorization header, exactly as before this
     * change; the peer decides how to treat it.
     */
    private static String defaultKeycloakToken() {
        try {
            OidcClients oidcClients = CDI.current().select(OidcClients.class).get();
            OidcClient oidcClient = oidcClients.getClient("service-client");
            Tokens tokens = oidcClient.getTokens().await().indefinitely();
            return tokens.getAccessToken();
        } catch (Exception e) {
            Log.errorf(e, "Failed to obtain access token via client credentials");
            return null;
        }
    }
}
