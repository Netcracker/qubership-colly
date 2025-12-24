package org.qubership.colly.uiservice.dto;

public record NamespaceDto(
        String id,
        String name,
        Boolean existsInK8s) {
}
