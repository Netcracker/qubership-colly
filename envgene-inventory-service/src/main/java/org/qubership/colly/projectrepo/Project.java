package org.qubership.colly.projectrepo;

import java.util.List;

public record Project(String id, String name, ProjectType type, String customerName, List<InstanceRepository> instanceRepositories, ClusterPlatform clusterPlatform) {
}
