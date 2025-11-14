package org.qubership.colly.uiservice.dto.operational;

public record OperationalNamespaceDto(
        String id,
        String name,
        boolean existsInK8s) {
}
