package org.qubership.colly.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = "Environment operational information including monitoring data and deployment details")
public record EnvironmentDTO(
        @Schema(
                description = "Unique identifier of the environment (UUID format)",
                examples = "96180fe7-f025-465f-bbbf-5e83f301a614",
                required = true
        )
        String id,

        @Schema(
                description = "Name of the environment",
                examples = "prod-env-1",
                required = true
        )
        String name,

        @Schema(
                description = "List of Kubernetes namespaces in this environment with operational status",
                required = true
        )
        List<NamespaceDTO> namespaces,

        @Schema(
                description = "Kubernetes cluster where this environment is deployed with sync status",
                required = true
        )
        ClusterDTO cluster,

        @Schema(
                description = "Current deployment version from k8s data",
                examples = "1.2.3",
                nullable = true
        )
        String deploymentVersion,

        @Schema(
                description = "Timestamp of the clean installation (ISO 8601 format)",
                examples = "2024-01-15T10:30:00Z",
                nullable = true
        )
        Instant cleanInstallationDate,

        @Schema(
                description = "Additional monitoring metrics and data collected from the environment",
                examples = "{\"cpu_usage\": \"75%\", \"memory_usage\": \"60%\", \"pod_count\": \"42\"}",
                nullable = true
        )
        Map<String, String> monitoringData) {

}
