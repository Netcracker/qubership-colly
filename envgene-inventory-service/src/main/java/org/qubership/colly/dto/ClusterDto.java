package org.qubership.colly.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Kubernetes cluster information")
public record ClusterDto(
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
        String name
) {
}
