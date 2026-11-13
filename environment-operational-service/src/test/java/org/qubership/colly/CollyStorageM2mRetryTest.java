package org.qubership.colly;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.colly.cloudpassport.ClusterInfo;
import org.qubership.colly.db.repository.ClusterRepository;
import org.qubership.colly.db.repository.EnvironmentRepository;
import org.qubership.colly.mapper.EnvironmentMapper;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for the 401 retry half of the Fallback Algorithm in
 * CollyStorage#fetchClusterInfos (docs/design/m2m-kubernetes/0.transition-plan.md
 * section 0.2). The request half is simulated with M2mTokenRoute the same way
 * M2mRequestFilter sets it on the caller thread; the rest client itself is mocked.
 * The 2xx decision cache is asserted in M2mRequestFilterTest (response half).
 */
class CollyStorageM2mRetryTest {

    private static final String M2M_AUTH_MODE = "M2M_AUTH_MODE";
    private static final URI INVENTORY_URI =
            URI.create("http://envgene-inventory-service/colly/v2/inventory-service/internal/cluster-infos");

    private EnvgeneInventoryServiceRest rest;
    private CollyStorage storage;

    @BeforeEach
    void setUp() {
        assumeTrue(System.getenv(M2M_AUTH_MODE) == null,
                "M2M_AUTH_MODE is set in the environment; the system-property driven test would be unreliable");
        rest = mock(EnvgeneInventoryServiceRest.class);
        storage = new CollyStorage(
                mock(ClusterResourcesLoader.class),
                mock(ClusterRepository.class),
                mock(EnvironmentRepository.class),
                mock(EnvironmentMapper.class),
                mock(ClusterSyncLock.class),
                rest);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(M2M_AUTH_MODE);
        M2mTokenRoute.endRequest();
        M2mTokenRoute.cache().invalidateAll();
    }

    private void simulateRequestHalf(M2mTokenRoute.Route route) {
        M2mTokenRoute.beginRequest(INVENTORY_URI);
        switch (route) {
            case USER_TOKEN -> M2mTokenRoute.markUserToken();
            case KUBERNETES -> M2mTokenRoute.markKubernetes();
            case KEYCLOAK -> M2mTokenRoute.markKeycloak(null);
        }
    }

    private WebApplicationException unauthorized() {
        return new WebApplicationException("unauthorized", Response.status(401).build());
    }

    @Test
    void hybrid_401_retries_once_with_keycloak_token() {
        System.setProperty(M2M_AUTH_MODE, "hybrid");
        simulateRequestHalf(M2mTokenRoute.Route.KUBERNETES);
        when(rest.getClusterInfos()).thenThrow(unauthorized()).thenReturn(List.of());

        List<ClusterInfo> result = storage.fetchClusterInfos();

        assertTrue(result.isEmpty());
        verify(rest, times(2)).getClusterInfos();
    }

    @Test
    void hybrid_401_after_failed_retry_propagates_the_second_401() {
        System.setProperty(M2M_AUTH_MODE, "hybrid");
        simulateRequestHalf(M2mTokenRoute.Route.KUBERNETES);
        when(rest.getClusterInfos()).thenThrow(unauthorized()).thenThrow(unauthorized());

        WebApplicationException e = org.junit.jupiter.api.Assertions.assertThrows(
                WebApplicationException.class, () -> storage.fetchClusterInfos());

        assertEquals(401, e.getResponse().getStatus());
        verify(rest, times(2)).getClusterInfos();
    }

    @Test
    void k8s_mode_401_does_not_retry() {
        // k8s mode: the 401 means the peer is not migrated; the design doc is
        // explicit that the client must not fall back — the 401 propagates as is.
        System.setProperty(M2M_AUTH_MODE, "k8s");
        simulateRequestHalf(M2mTokenRoute.Route.KUBERNETES);
        when(rest.getClusterInfos()).thenThrow(unauthorized());

        WebApplicationException e = org.junit.jupiter.api.Assertions.assertThrows(
                WebApplicationException.class, () -> storage.fetchClusterInfos());

        assertEquals(401, e.getResponse().getStatus());
        verify(rest, times(1)).getClusterInfos();
    }

    @Test
    void keycloak_route_401_does_not_retry() {
        // A 401 on the keycloak route means the keycloak token itself is bad;
        // retrying with keycloak again is pointless (platform behavior: only
        // KUBERNETES-routed 401s are retried).
        System.setProperty(M2M_AUTH_MODE, "hybrid");
        simulateRequestHalf(M2mTokenRoute.Route.KEYCLOAK);
        when(rest.getClusterInfos()).thenThrow(unauthorized());

        org.junit.jupiter.api.Assertions.assertThrows(
                WebApplicationException.class, () -> storage.fetchClusterInfos());

        verify(rest, times(1)).getClusterInfos();
    }

    @Test
    void user_token_401_does_not_retry() {
        // User-authentication failures must never be silently re-sent as M2M.
        System.setProperty(M2M_AUTH_MODE, "hybrid");
        simulateRequestHalf(M2mTokenRoute.Route.USER_TOKEN);
        when(rest.getClusterInfos()).thenThrow(unauthorized());

        org.junit.jupiter.api.Assertions.assertThrows(
                WebApplicationException.class, () -> storage.fetchClusterInfos());

        verify(rest, times(1)).getClusterInfos();
    }

    @Test
    void non_401_web_application_exception_is_not_retried() {
        System.setProperty(M2M_AUTH_MODE, "hybrid");
        simulateRequestHalf(M2mTokenRoute.Route.KUBERNETES);
        when(rest.getClusterInfos())
                .thenThrow(new WebApplicationException("not found", Response.Status.NOT_FOUND));

        WebApplicationException e = org.junit.jupiter.api.Assertions.assertThrows(
                WebApplicationException.class, () -> storage.fetchClusterInfos());

        assertEquals(404, e.getResponse().getStatus());
        verify(rest, times(1)).getClusterInfos();
    }
}
