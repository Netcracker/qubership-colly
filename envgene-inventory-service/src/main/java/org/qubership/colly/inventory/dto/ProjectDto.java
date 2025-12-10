package org.qubership.colly.inventory.dto;

import org.qubership.colly.inventory.projectrepo.ClusterPlatform;
import org.qubership.colly.inventory.projectrepo.ProjectType;

import java.util.List;

public record ProjectDto(String id,
                         String name,
                         ProjectType type,
                         String customerName,
                         List<InstanceRepositoryDto> instanceRepositories,
                         ClusterPlatform clusterPlatform) {
}
