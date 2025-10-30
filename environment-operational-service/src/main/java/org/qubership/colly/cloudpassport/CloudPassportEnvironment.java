package org.qubership.colly.cloudpassport;

import java.time.LocalDate;
import java.util.List;

public record CloudPassportEnvironment(String name,
                                       String description,
                                       List<CloudPassportNamespace> namespaces,
                                       List<String> owners,
                                       List<String> labels,
                                       List<String> teams,
                                       String status,
                                       LocalDate expirationDate,
                                       String environmentType,
                                       String role) {
}
