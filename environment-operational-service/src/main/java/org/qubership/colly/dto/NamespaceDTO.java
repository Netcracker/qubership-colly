package org.qubership.colly.dto;


public record NamespaceDTO(String uid, String name, boolean existsInK8s) {
}
