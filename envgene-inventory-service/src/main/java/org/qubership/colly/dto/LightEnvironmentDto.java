package org.qubership.colly.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema(description = "Lightweight environment information without full details")
public record LightEnvironmentDto(
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
                description = "List of Kubernetes namespaces in this environment",
                nullable = true
        )
        List<NamespaceDto> namespaces) {
}
