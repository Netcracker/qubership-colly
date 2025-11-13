package org.qubership.colly.dto;


public record NamespaceDTO(String id, String name, boolean existsInK8s) {
}
