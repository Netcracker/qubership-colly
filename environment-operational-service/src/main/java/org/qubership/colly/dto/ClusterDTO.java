package org.qubership.colly.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Kubernetes cluster operational information")
public record ClusterDTO(
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
        @Schema(description = "Count of nodes for kubernetes cluster",
                examples = "3")
        Integer numberOfNodes,

        @Schema(
                description = "Indicates whether the cluster has been synchronized with Kubernetes API",
                examples = "true",
                required = true
        )
        boolean synced) {
}
