package org.qubership.colly.cloudpassport;

import org.qubership.colly.db.data.EnvironmentStatus;
import org.qubership.colly.db.data.EnvironmentType;

import java.time.LocalDate;
import java.util.List;

public record CloudPassportEnvironment(String name,
                                       String description,
                                       List<CloudPassportNamespace> namespaces,
                                       List<String> owners,
                                       List<String> labels,
                                       List<String> teams,
                                       EnvironmentStatus status,
                                       LocalDate expirationDate,
                                       EnvironmentType environmentType,
                                       String role) {
}
