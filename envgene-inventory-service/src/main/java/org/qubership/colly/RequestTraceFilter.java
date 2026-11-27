package org.qubership.colly;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logmanager.MDC;

/**
 * Puts the trace fields required by the release log-traceability validation into
 * the MDC for the duration of one HTTP request: requestId from the X-Request-ID
 * header and tenantId from the X-Tenant-Id header. Missing headers are not
 * invented (no generated ids, no placeholders) - the checker tolerates empty
 * field values. The MDC key names are the contract with the %X lookups in
 * quarkus.log.console.format. All resources of this service are synchronous,
 * so the response filter runs on the request thread and clears the MDC there.
 */
@Provider
public class RequestTraceFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String REQUEST_ID_KEY = "requestId";
    public static final String TENANT_ID_KEY = "tenantId";
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        // Clear first: worker threads are pooled, and a response filter skipped on an
        // aborted request must not leak its ids into this request.
        clearTraceMdc();
        putIfPresent(REQUEST_ID_KEY, requestContext.getHeaderString(REQUEST_ID_HEADER));
        putIfPresent(TENANT_ID_KEY, requestContext.getHeaderString(TENANT_ID_HEADER));
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        clearTraceMdc();
    }

    private static void clearTraceMdc() {
        MDC.remove(REQUEST_ID_KEY);
        MDC.remove(TENANT_ID_KEY);
    }

    private static void putIfPresent(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }
}
