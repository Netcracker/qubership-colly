package org.qubership.colly.cloudpassport;

import java.util.List;

public record CloudPassportEnvironment(String name, String owner, String description, List<CloudPassportNamespace> namespaces) {
}
