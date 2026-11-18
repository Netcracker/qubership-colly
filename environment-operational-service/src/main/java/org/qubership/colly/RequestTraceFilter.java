package org.qubership.colly;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.util.UUID;

/**
 * Puts the trace fields required by the Cloud Release log_traceability validation
 * (PDPLDEVOPS-27279) into the MDC for the duration of one HTTP request: requestId
 * (X-Request-ID header, or a generated UUID when absent) and tenantId (X-Tenant-Id
 * header, or the "-" placeholder). All resources of this service are synchronous,
 * so the response filter runs on the request thread and clears the MDC there.
 */
@Provider
public class RequestTraceFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String TENANT_ID_HEADER = "X-Tenant-Id";

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String requestId = requestContext.getHeaderString(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        String tenantId = requestContext.getHeaderString(TENANT_ID_HEADER);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = TraceMdc.NO_TENANT;
        }
        TraceMdc.put(requestId, tenantId);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        TraceMdc.clear();
    }
}
