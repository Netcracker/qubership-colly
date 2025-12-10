package org.qubership.colly.uiservice.dto;

public record ClusterDto(
        String id,
        String name,
        Boolean synced,
        String dashboardUrl,
        String dbaasUrl,
        String deployerUrl) {
}
