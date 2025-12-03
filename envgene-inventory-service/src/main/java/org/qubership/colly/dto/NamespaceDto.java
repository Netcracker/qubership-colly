package org.qubership.colly.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Kubernetes namespace information")
public record NamespaceDto(
        @Schema(
                description = "Unique identifier of the namespace (UUID format)",
                example = "34f89c4d-bcc3-4eff-b271-6fdcdaf977c9",
                required = true
        )
        String id,

        @Schema(
                description = "Name of the Kubernetes namespace",
                example = "prod-app",
                required = true
        )
        String name
) {
}
