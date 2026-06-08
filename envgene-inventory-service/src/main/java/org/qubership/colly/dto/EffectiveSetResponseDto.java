package org.qubership.colly.dto;

import java.util.Map;

public record EffectiveSetResponseDto(
        String context,
        String environmentId,
        String namespaceName,
        String applicationName,
        Map<String, Object> parameters
) {
}
