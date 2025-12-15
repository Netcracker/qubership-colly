package org.qubership.colly;

import io.quarkus.logging.Log;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.Tokens;
import io.quarkus.oidc.client.OidcClients;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

/**
 * ClientHeadersFactory for operational-service that propagates user tokens
 * and falls back to service-to-service authentication via OIDC Client Credentials flow
 * when no user token is present (e.g., for scheduled tasks).
 */
@ApplicationScoped
public class OidcClientHeadersFactory implements ClientHeadersFactory {

    @Inject
    OidcClients oidcClients;

    @Override
    public MultivaluedMap<String, String> update(MultivaluedMap<String, String> incomingHeaders,
                                                 MultivaluedMap<String, String> clientOutgoingHeaders) {
        MultivaluedMap<String, String> result = new MultivaluedHashMap<>();

        // First, try to propagate the user's token from incoming request
        if (incomingHeaders != null && incomingHeaders.containsKey("Authorization")) {
            String authHeader = incomingHeaders.getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                result.add("Authorization", authHeader);
                Log.debugf("Propagating user token to inventory-service");
                return result;
            }
        }

        // Fall back to client credentials flow for service-to-service calls (e.g., scheduled tasks)
        try {
            // Get the named OIDC client configured for service-to-service calls
            OidcClient oidcClient = oidcClients.getClient("service-client");
            Tokens tokens = oidcClient.getTokens().await().indefinitely();
            String accessToken = tokens.getAccessToken();

            if (accessToken != null && !accessToken.isEmpty()) {
                result.add("Authorization", "Bearer " + accessToken);
                Log.debugf("Propagating service access token via client credentials");
            } else {
                Log.warn("Failed to obtain access token via client credentials");
            }
        } catch (Exception e) {
            Log.errorf(e, "Failed to obtain access token via client credentials");
        }

        return result;
    }
}
