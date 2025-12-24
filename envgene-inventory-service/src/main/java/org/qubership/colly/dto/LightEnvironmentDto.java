package org.qubership.colly.dto;

import java.util.List;

public record LightEnvironmentDto(String id, String name, List<NamespaceDto> namespaces) {
}
