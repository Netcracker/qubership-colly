package org.qubership.colly.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Effective Set response with per-parameter metadata.")
public record EffectiveSetResponseDto(
        @Schema(description = "Parameter context: `deployment`, `runtime`, or `pipeline`.")
        String context,

        @Schema(description = "UUID of the environment.")
        String environmentId,

        @Schema(description = "Namespace name. Absent (null) for `pipeline` context.", nullable = true)
        String namespaceName,

        @Schema(description = "Application name. Absent (null) for `pipeline` context.", nullable = true)
        String applicationName,

        @Schema(
                description = "Wrapped parameter tree. Each entry is an EffectiveSetParameter node: " +
                        "`{\"_type\": \"leaf\" | \"container\", \"_data\": {...}}`. " +
                        "Leaf `_data` contains `value`, `state`, and `originalValue`. " +
                        "Container `_data` contains nested EffectiveSetParameter nodes."
        )
        Map<String, Object> parameters
) {
}
