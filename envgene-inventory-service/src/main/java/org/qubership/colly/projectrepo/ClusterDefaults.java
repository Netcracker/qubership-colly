package org.qubership.colly.projectrepo;

import java.util.List;

public record ClusterDefaults(List<String> owners, List<String> roAdGroups, List<String> rwAdGroups) {
}
