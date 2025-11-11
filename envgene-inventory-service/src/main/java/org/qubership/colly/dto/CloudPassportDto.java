package org.qubership.colly.dto;

import java.util.List;

public record CloudPassportDto(String id,
                               String name,
                               String token,
                               String cloudApiHost,
                               List<LightEnvironmentDto> environments,
                               String monitoringUrl) {
}
