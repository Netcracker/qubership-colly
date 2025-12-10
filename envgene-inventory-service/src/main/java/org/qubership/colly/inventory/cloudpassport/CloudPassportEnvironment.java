package org.qubership.colly.inventory.cloudpassport;

import org.qubership.colly.inventory.db.data.EnvironmentStatus;
import org.qubership.colly.inventory.db.data.EnvironmentType;

import java.time.LocalDate;
import java.util.List;

public record CloudPassportEnvironment(String name,
                                       String description,
                                       List<CloudPassportNamespace> namespaceDtos,
                                       List<String> owners,
                                       List<String> labels,
                                       List<String> teams,
                                       EnvironmentStatus status,
                                       LocalDate expirationDate,
                                       EnvironmentType type,
                                       String role,
                                       String region) {
}
