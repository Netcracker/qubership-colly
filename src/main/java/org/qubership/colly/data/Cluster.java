package org.qubership.colly.data;

import java.util.List;

public record Cluster(String name, List<Namespace> namespaces) {
}
