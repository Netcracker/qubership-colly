package org.qubership.colly.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.qubership.colly.projectrepo.ClusterPlatform;
import org.qubership.colly.projectrepo.ProjectType;

import java.util.List;

@Schema(description = "Project information containing all project details and associated resources")
public record ProjectDto(
        @Schema(
                description = "Unique identifier of the project",
                examples = "project-123",
                required = true
        )
        String id,

        @Schema(
                description = "Name of the project",
                examples = "project-1",
                required = true
        )
        String name,

        @Schema(
                description = "Type of the project",
                enumeration = {"PRODUCT", "PROJECT"},
                required = true
        )
        ProjectType type,

        @Schema(
                description = "Name of the customer/organization owning this project",
                examples = "Acme Corporation",
                nullable = true
        )
        String customerName,

        @Schema(
                description = "List of instance repositories associated with the project",
                nullable = true
        )
        List<InstanceRepositoryDto> instanceRepositories,

        @Schema(
                description = "List of CI/CD pipelines configured for the project",
                nullable = true
        )
        List<PipelineDto> pipelines,

        @Schema(
                description = "Target cluster platform for deployment",
                enumeration = {"OCP", "K8S"},
                nullable = true
        )
        ClusterPlatform clusterPlatform,

        @Schema(
                description = "Template repository configuration",
                nullable = true
        )
        TemplateRepositoryDto templateRepository,

        @Schema(
                description = "List of access groups that have permissions to this project",
                examples = "[\"developers\", \"admins\"]",
                nullable = true
        )
        List<String> accessGroups) {
}
