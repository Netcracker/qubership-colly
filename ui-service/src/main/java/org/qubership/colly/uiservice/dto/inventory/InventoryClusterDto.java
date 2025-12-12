package org.qubership.colly.uiservice.dto.inventory;

public record InventoryClusterDto(
        String id,
        String name,
        String dashboardUrl,
        String dbaasUrl,
        String deployerUrl,
        String argoUrl) {
}
