package org.qubership.colly.uiservice.dto.inventory;

import org.qubership.colly.uiservice.dto.EnvironmentStatus;
import org.qubership.colly.uiservice.dto.EnvironmentType;

import java.time.LocalDate;
import java.util.List;

/**
 * Environment DTO from inventory-service (metadata)
 */
public record InventoryEnvironmentDto(
        String id,
        String name,
        String description,
        List<InventoryNamespaceDto> namespaces,
        InventoryClusterDto cluster,
        List<String> owners,
        List<String> labels,
        List<String> teams,
        EnvironmentStatus status,
        LocalDate expirationDate,
        EnvironmentType type,
        String role,
        String region) {
}
