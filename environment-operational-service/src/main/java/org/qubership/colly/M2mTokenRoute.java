package org.qubership.colly;

import com.netcracker.cloud.security.common.token.m2m.cache.UrlCache;

import java.net.URI;

/**
 * Per-request M2M token-route bookkeeping for the envgene-inventory-service client (PDPLDEVOPS-25309).
 * <p>
 * The route is written on the calling thread while the request is built (by
 * {@link OidcClientHeadersFactory} or {@link M2mRequestFilter}) and read back in
 * {@code CollyStorage#fetchClusterInfos()} after the call to drive the 401 retry
 * from the transition plan (docs/design/m2m-kubernetes/0.transition-plan.md,
 * section 0.2 "Fallback Algorithm"). The per-URL decision cache is app-wide and is
 * stored from the response filter (a different thread) via the request property
 * set by {@link M2mRequestFilter}; this ThreadLocal is never read from another
 * thread.
 * <p>
 * Same-thread handoff mirrors the platform's M2MFilter FALLBACK_CONTEXT ThreadLocal
 * (security-quarkus-extensions). The URL cache is a single app-wide instance: 400
 * entries, 5h lifetime, LRU — the platform's UrlCache.
 */
final class M2mTokenRoute {

    enum Route { USER_TOKEN, KUBERNETES, KEYCLOAK }

    /** Why a keycloak token was used while k8s M2M was enabled. */
    enum KeycloakReason { ACQUISITION_FALLBACK, UNAUTHORIZED_RETRY }

    private static final UrlCache URL_CACHE = new UrlCache();

    private static final ThreadLocal<Route> ROUTE = new ThreadLocal<>();
    private static final ThreadLocal<KeycloakReason> KEYCLOAK_REASON = new ThreadLocal<>();
    private static final ThreadLocal<String> CACHED_KEY = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> FORCE_KEYCLOAK = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private M2mTokenRoute() {
    }

    /** Begins M2M route tracking for a request; called by {@link M2mRequestFilter}. */
    static void beginRequest(URI uri) {
        ROUTE.remove();
        KEYCLOAK_REASON.remove();
        CACHED_KEY.set(UrlCache.calculateCacheKey(uri));
    }

    /** Ends route tracking for a request; called from {@code CollyStorage} finally. */
    static void endRequest() {
        ROUTE.remove();
        KEYCLOAK_REASON.remove();
        CACHED_KEY.remove();
        FORCE_KEYCLOAK.remove();
    }

    static void markUserToken() {
        ROUTE.set(Route.USER_TOKEN);
    }

    static void markKubernetes() {
        ROUTE.set(Route.KUBERNETES);
    }

    static void markKeycloak(KeycloakReason reason) {
        ROUTE.set(Route.KEYCLOAK);
        KEYCLOAK_REASON.set(reason);
    }

    static Route route() {
        return ROUTE.get();
    }

    static KeycloakReason keycloakReason() {
        return KEYCLOAK_REASON.get();
    }

    static String cachedKey() {
        return CACHED_KEY.get();
    }

    /** Makes the in-flight retry use the keycloak token regardless of the cache. */
    static void forceKeycloakOnce() {
        FORCE_KEYCLOAK.set(Boolean.TRUE);
    }

    static boolean isForcedKeycloak() {
        return FORCE_KEYCLOAK.get();
    }

    static UrlCache cache() {
        return URL_CACHE;
    }
}
