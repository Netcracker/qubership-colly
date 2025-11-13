package org.qubership.colly.dto;

import org.qubership.colly.db.data.EnvironmentStatus;
import org.qubership.colly.db.data.EnvironmentType;

import java.time.LocalDate;
import java.util.List;

public record EnvironmentDto(String id,
                             String name,
                             String description,
                             List<NamespaceDto> namespaces,
                             ClusterDto cluster,
                             List<String> owners,
                             List<String> labels,
                             List<String> teams,
                             EnvironmentStatus status,
                             LocalDate expirationDate,
                             EnvironmentType type,
                             String role) {
}
