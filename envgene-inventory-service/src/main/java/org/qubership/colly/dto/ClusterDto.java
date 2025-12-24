package org.qubership.colly.dto;

import java.util.List;

public record ClusterDto(String id,
                         String name,
                         List<LightEnvironmentDto> environments,
                         String dashboardUrl,
                         String dbaasUrl,
                         String deployerUrl,
                         String argoUrl) {
}
