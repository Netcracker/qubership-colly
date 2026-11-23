package org.qubership.colly;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

/**
 * Propagates the incoming user token to the envgene-inventory-service client.
 * <p>
 * When no user token is present this factory sets no Authorization header:
 * service-to-service (M2M) authentication is applied by
 * {@link M2mRequestFilter} (registered on the client), which needs the request
 * URI for the per-URL token-type cache and the per-request retry bookkeeping
 * in {@link M2mTokenRoute} (PDPLDEVOPS-25309). The filter skips requests that
 * already carry a user token, so the two layers cannot clobber each other
 * regardless of the order in which the client framework applies them.
 */
@ApplicationScoped
public class OidcClientHeadersFactory implements ClientHeadersFactory {

    @Override
    public MultivaluedMap<String, String> update(MultivaluedMap<String, String> incomingHeaders,
                                                 MultivaluedMap<String, String> clientOutgoingHeaders) {
        MultivaluedMap<String, String> result = new MultivaluedHashMap<>();

        // First, try to propagate the user's token from the incoming request
        if (incomingHeaders != null && incomingHeaders.containsKey("Authorization")) {
            String authHeader = incomingHeaders.getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                result.add("Authorization", authHeader);
                M2mTokenRoute.markUserToken();
                Log.debugf("Propagating user token to inventory-service");
                return result;
            }
        }

        return result;
    }
}
