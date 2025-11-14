package org.qubership.colly.uiservice.dto.operational;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Environment DTO from operational-service (runtime data)
 */
public record OperationalEnvironmentDto(
        String id,
        String name,
        List<OperationalNamespaceDto> namespaces,
        OperationalClusterDto cluster,
        String deploymentVersion,
        Instant cleanInstallationDate,
        Map<String, String> monitoringData) {
}
