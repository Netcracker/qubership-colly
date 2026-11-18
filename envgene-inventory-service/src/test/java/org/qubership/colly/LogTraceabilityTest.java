package org.qubership.colly;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.MDC;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * log_traceability gate (PDPLDEVOPS-27279).
 *
 * The Cloud Release LOGChecker matches each log line with regexes anchored to the start
 * of the line (usedRegex in the testrun report, BASS "Validations: Log Traceability"):
 * the line must start with [requestId=...] [tenant_id=...] [thread=...] [class=...]
 * (field values may be empty). This test renders a sample line through the exact console
 * format this service has configured (quarkus.log.console.format, with the same
 * jboss-logmanager PatternFormatter the running console handler uses) and asserts the
 * checker's rules. If the property is removed or the fields are reordered, this fails
 * here instead of at the next release run.
 */
@QuarkusTest
class LogTraceabilityTest {

    // The checker's own anchored regexes (verbatim from the LOGChecker testrun report).
    private static final Pattern REQUEST_ID = Pattern.compile("^\\s{0,}\\[\\s{0,}(requestId\\s*=\\s*[^\\]]*|x_request_id\\s*=\\s*[^\\]]*|request_id\\s*=\\s*[^\\]]*)\\s{0,}\\][\\s\\S]*$");
    private static final Pattern TENANT_ID = Pattern.compile("^\\s{0,}\\[[^\\]]+\\]\\s{0,}\\[\\s{0,}(tenant_id\\s*=\\s*[^\\]]*)\\s{0,}\\][\\s\\S]*$");
    private static final Pattern THREAD = Pattern.compile("^\\s{0,}\\[[^\\]]+\\]\\s{0,}\\[[^\\]]+\\]\\s{0,}\\[\\s{0,}(thread\\s*=\\s*(.*?))\\s{0,}\\][\\s\\S]*$");
    private static final Pattern CLASS_NAME = Pattern.compile("^\\s{0,}\\[[^\\]]+\\]\\s{0,}\\[[^\\]]+\\]\\s{0,}\\[[^\\]]+\\]\\s{0,}\\[\\s{0,}(class\\s*=\\s*[^\\]]*)\\s{0,}\\][\\s\\S]*$");
    // The timestamp/loglevel checks are not anchored: the time and a level keyword must be present in the line.
    private static final Pattern TIMESTAMP = Pattern.compile("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}[.,]\\d{3}");
    private static final Pattern LEVEL = Pattern.compile("\\b(INFO|ERROR|WARN|warning|DEBUG|FATAL)\\b");

    @AfterEach
    void clearMdc() {
        TraceMdc.clear();
    }

    @Test
    void request_without_headers_carry_generated_id_and_pass_the_checker() {
        RequestTraceFilter filter = new RequestTraceFilter();
        ContainerRequestContext request = mock(ContainerRequestContext.class);

        filter.filter(request);

        String requestId = MDC.get(TraceMdc.REQUEST_ID);
        assertNotNull(requestId, "requestId must be generated when X-Request-ID is absent");
        assertEquals(TraceMdc.NO_TENANT, MDC.get(TraceMdc.TENANT_ID));

        assertCheckerRules(formatLine("sample message for log_traceability"));
    }

    @Test
    void explicit_request_and_tenant_ids_are_used() {
        RequestTraceFilter filter = new RequestTraceFilter();
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        when(request.getHeaderString(RequestTraceFilter.REQUEST_ID_HEADER)).thenReturn("req-123");
        when(request.getHeaderString(RequestTraceFilter.TENANT_ID_HEADER)).thenReturn("tenant-42");

        filter.filter(request);

        String line = formatLine("sample message for log_traceability");
        assertTrue(line.contains("[requestId=req-123]"), line);
        assertTrue(line.contains("[tenant_id=tenant-42]"), line);
        assertCheckerRules(line);
    }

    @Test
    void response_half_clears_the_mdc() {
        RequestTraceFilter filter = new RequestTraceFilter();
        ContainerRequestContext request = mock(ContainerRequestContext.class);

        filter.filter(request);
        filter.filter(request, mock(ContainerResponseContext.class));

        assertNull(MDC.get(TraceMdc.REQUEST_ID));
        assertNull(MDC.get(TraceMdc.TENANT_ID));
    }

    @Test
    void out_of_scope_lines_still_match_all_rules() {
        // No MDC (startup/framework lines): the checker regexes accept empty field
        // values, the [requestId=]/[tenant_id=] labels keep the bracket content non-empty.
        assertCheckerRules(formatLine("sample message for log_traceability"));
    }

    private static void assertCheckerRules(String line) {
        assertAll(line,
                () -> assertTrue(REQUEST_ID.matcher(line).matches(), "requestid"),
                () -> assertTrue(TENANT_ID.matcher(line).matches(), "tenantid"),
                () -> assertTrue(THREAD.matcher(line).matches(), "threadid"),
                () -> assertTrue(CLASS_NAME.matcher(line).matches(), "classname"),
                () -> assertTrue(TIMESTAMP.matcher(line).find(), "timestamp"),
                () -> assertTrue(LEVEL.matcher(line).find(), "loglevel"));
    }

    private static String formatLine(String message) {
        String pattern = ConfigProvider.getConfig()
                .getOptionalValue("quarkus.log.console.format", String.class)
                .orElseThrow(() -> new AssertionError(
                        "quarkus.log.console.format is not set; log_traceability (PDPLDEVOPS-27279) would fail"));
        PatternFormatter formatter = new PatternFormatter(pattern);
        ExtLogRecord record = new ExtLogRecord(Level.INFO, message, "org.qubership.colly.LogTraceabilityTest");
        return formatter.format(record).stripTrailing();
    }
}
