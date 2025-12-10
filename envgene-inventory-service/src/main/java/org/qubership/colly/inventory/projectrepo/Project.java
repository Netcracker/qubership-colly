package org.qubership.colly.inventory.projectrepo;

import java.util.List;

public record Project(String id, String name, ProjectType type, String customerName, List<InstanceRepository> instanceRepositories, ClusterPlatform clusterPlatform) {
}
