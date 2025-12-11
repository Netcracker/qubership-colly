package org.qubership.colly.uiservice.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Aggregated environment DTO combining data from inventory and operational services
 */
public record EnvironmentDto(
        // Fields from inventory-service
        String id,
        String name,
        String description,
        List<NamespaceDto> namespaces,
        ClusterViewDto cluster,
        List<String> owners,
        List<String> labels,
        List<String> teams,
        EnvironmentStatus status,
        LocalDate expirationDate,
        EnvironmentType type,
        String role,
        String region,

        // Fields from operational-service
        String deploymentVersion,
        Instant cleanInstallationDate,
        Map<String, String> monitoringData) {
}
