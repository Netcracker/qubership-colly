package org.qubership.colly.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema(description = "Internal cluster information including sensitive data like access tokens. This is used for service-to-service communication.")
public record InternalClusterInfoDto(
        @Schema(
                description = "Unique identifier of the cluster (UUID format)",
                examples = "995f5292-5725-42b6-ad28-0e8629e0f791",
                required = true
        )
        String id,

        @Schema(
                description = "Name of the Kubernetes cluster",
                examples = "prod-cluster-01",
                required = true
        )
        String name,

        @Schema(
                description = "Service account token for accessing the cluster API",
                examples = "eyJhbGciOiJSUzI1NiIsImtpZCI6Ik...",
                required = true
        )
        String token,

        @Schema(
                description = "Kubernetes API server host URL",
                examples = "https://1E4A399FCB54F505BBA05320EADF0DB3.gr7.eu-west-1.eks.amazonaws.com:443",
                required = true
        )
        String cloudApiHost,

        @Schema(
                description = "List of environments deployed on this cluster",
                nullable = true
        )
        List<LightEnvironmentDto> environments,

        @Schema(
                description = "Monitoring service URL (e.g., VictoriaMetrics, Prometheus)",
                examples = "http://vmsingle-k8s.victoria:8428",
                nullable = true
        )
        String monitoringUrl) {
}
