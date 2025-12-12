package org.qubership.colly.dto;

public record ClusterDto(String id,
                         String name,
                         String dashboardUrl,
                         String dbaasUrl,
                         String deployerUrl,
                         String argoUrl) {
}
