package org.qubership.colly.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.Map;

@Schema(description = "Uncommitted UI parameters to overlay on top of the Effective Set.")
public record EffectiveSetRequestDto(
        @Schema(
                description = "Map of parameter key-value pairs to overlay over the cached Effective Set. " +
                        "Supports nested maps for hierarchical parameters. " +
                        "A null value for a key is treated as a literal null, not a deletion. " +
                        "May be omitted or empty."
        )
        Map<String, Object> parameters
) {
}
