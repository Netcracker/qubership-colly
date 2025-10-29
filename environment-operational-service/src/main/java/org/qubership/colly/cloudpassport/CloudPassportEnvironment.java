package org.qubership.colly.cloudpassport;

import java.util.List;

public record CloudPassportEnvironment(String name, String description, List<CloudPassportNamespace> namespaces,
                                       List<String> owners, List<String> labels, List<String> teams) {
}
