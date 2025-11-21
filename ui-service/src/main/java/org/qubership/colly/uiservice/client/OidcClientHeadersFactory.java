package org.qubership.colly.uiservice.client;

import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

@ApplicationScoped
public class OidcClientHeadersFactory implements ClientHeadersFactory {

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    JsonWebToken jwt;

    @Override
    public MultivaluedMap<String, String> update(MultivaluedMap<String, String> incomingHeaders,
                                                 MultivaluedMap<String, String> clientOutgoingHeaders) {
        MultivaluedMap<String, String> result = new MultivaluedHashMap<>();

        // If user is authenticated, extract and propagate the access token
        if (securityIdentity != null && !securityIdentity.isAnonymous()) {
            try {
                // Get the raw JWT token
                String accessToken = jwt.getRawToken();

                if (accessToken != null && !accessToken.isEmpty()) {
                    result.add("Authorization", "Bearer " + accessToken);
                    Log.debugf("Propagating access token for user: %s", securityIdentity.getPrincipal().getName());
                } else {
                    Log.warnf("Access token is null or empty for user: %s",
                            securityIdentity.getPrincipal().getName());
                }
            } catch (Exception e) {
                Log.errorf(e, "Failed to extract access token for user: %s",
                        securityIdentity.getPrincipal().getName());
            }
        } else {
            Log.debug("No authenticated user, skipping token propagation");
        }

        return result;
    }
}
