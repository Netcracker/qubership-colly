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
 * ClientHeadersFactory for operational-service that uses service-to-service authentication
 * via OIDC Client Credentials flow.
 */
@ApplicationScoped
public class OidcClientHeadersFactory implements ClientHeadersFactory {

    @Inject
    OidcClients oidcClients;

    @Override
    public MultivaluedMap<String, String> update(MultivaluedMap<String, String> incomingHeaders,
                                                 MultivaluedMap<String, String> clientOutgoingHeaders) {
        MultivaluedMap<String, String> result = new MultivaluedHashMap<>();

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
