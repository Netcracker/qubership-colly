package org.qubership.colly;

import org.jboss.logmanager.MDC;

/**
 * MDC population for the Cloud Release log_traceability validation (PDPLDEVOPS-27279).
 * The console format renders these keys through jboss-logmanager %X lookups: slf4j
 * (io.quarkus.logging.Log) is bridged to org.jboss.logmanager.MDC, so that is the
 * MDC the running console formatter reads.
 */
public final class TraceMdc {

    public static final String REQUEST_ID = "requestId";
    public static final String TENANT_ID = "tenantId";
    /** Placeholder for a unit of work without a tenant context (NC convention). */
    public static final String NO_TENANT = "-";

    private TraceMdc() {
    }

    public static void put(String requestId, String tenantId) {
        MDC.put(REQUEST_ID, requestId);
        MDC.put(TENANT_ID, tenantId);
    }

    public static void clear() {
        MDC.remove(REQUEST_ID);
        MDC.remove(TENANT_ID);
    }
}
