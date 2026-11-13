package org.qubership.colly;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Plain unit test (no @QuarkusTest): OidcClientHeadersFactory only propagates the
 * incoming user token; the M2M token selection lives in M2mRequestFilter (tested in
 * M2mRequestFilterTest), which needs the request URI that this factory cannot see.
 */
class OidcClientHeadersFactoryTest {

    private static final String M2M_AUTH_MODE = "M2M_AUTH_MODE";

    private final OidcClientHeadersFactory factory = new OidcClientHeadersFactory();

    @AfterEach
    void tearDown() {
        System.clearProperty(M2M_AUTH_MODE);
        M2mTokenRoute.endRequest();
    }

    @Test
    void propagates_user_token_when_present() {
        MultivaluedMap<String, String> incoming = new MultivaluedHashMap<>();
        incoming.add("Authorization", "Bearer user-token");

        MultivaluedMap<String, String> result = factory.update(incoming, new MultivaluedHashMap<>());

        assertEquals("Bearer user-token", result.getFirst("Authorization"));
        assertEquals(M2mTokenRoute.Route.USER_TOKEN, M2mTokenRoute.route());
    }

    @Test
    void propagates_user_token_in_hybrid_mode_m2m_must_not_clobber_it() {
        // The filter skips requests that already carry an Authorization header,
        // so the user token must survive in hybrid mode as well.
        System.setProperty(M2M_AUTH_MODE, "hybrid");
        MultivaluedMap<String, String> incoming = new MultivaluedHashMap<>();
        incoming.add("Authorization", "Bearer user-token");

        MultivaluedMap<String, String> result = factory.update(incoming, new MultivaluedHashMap<>());

        assertEquals("Bearer user-token", result.getFirst("Authorization"));
        assertEquals(M2mTokenRoute.Route.USER_TOKEN, M2mTokenRoute.route());
    }

    @Test
    void no_user_token_sets_no_authorization_header() {
        // No user token: the factory stays silent and M2mRequestFilter supplies
        // the service-to-service token (keycloak in legacy mode, k8s/kc in hybrid).
        MultivaluedMap<String, String> result = factory.update(new MultivaluedHashMap<>(), new MultivaluedHashMap<>());

        assertNull(result.getFirst("Authorization"));
        assertNull(M2mTokenRoute.route());
    }

    @Test
    void non_bearer_authorization_header_is_ignored() {
        MultivaluedMap<String, String> incoming = new MultivaluedHashMap<>();
        incoming.add("Authorization", "Basic dXNlcjpwdw==");

        MultivaluedMap<String, String> result = factory.update(incoming, new MultivaluedHashMap<>());

        assertNull(result.getFirst("Authorization"));
    }
}
