package org.qubership.colly.dto;

import java.util.List;

public record InternalClusterInfoDto(String id,
                                     String name,
                                     String token,
                                     String cloudApiHost,
                                     List<LightEnvironmentDto> environments,
                                     String monitoringUrl,
                                     String dashboardUrl,
                                     String dbaasUrl,
                                     String deployerUrl) {
}
