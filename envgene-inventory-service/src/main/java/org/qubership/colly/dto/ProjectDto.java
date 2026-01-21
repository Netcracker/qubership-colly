package org.qubership.colly.dto;

import org.qubership.colly.projectrepo.ClusterPlatform;
import org.qubership.colly.projectrepo.ProjectType;

import java.util.List;

public record ProjectDto(String id,
                         String name,
                         ProjectType type,
                         String customerName,
                         List<InstanceRepositoryDto> instanceRepositories,
                         List<PipelineDto> pipelines,
                         ClusterPlatform clusterPlatform,
                         TemplateRepositoryDto templateRepository,
                         List<String> accessGroups) {
}
