package org.qubership.colly.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Kubernetes namespace operational information")
public record NamespaceDTO(
        @Schema(
                description = "Unique identifier of the namespace (UUID format)",
                examples = "34f89c4d-bcc3-4eff-b271-6fdcdaf977c9",
                required = true
        )
        String id,

        @Schema(
                description = "Name of the Kubernetes namespace",
                examples = "prod-app",
                required = true
        )
        String name,

        @Schema(
                description = "Indicates whether the namespace actually exists in Kubernetes cluster",
                examples = "true",
                required = true
        )
        boolean existsInK8s) {
}
