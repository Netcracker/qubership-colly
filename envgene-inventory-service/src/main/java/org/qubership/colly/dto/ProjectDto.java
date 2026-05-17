package org.qubership.colly.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema(description = "Project information containing project details and associated resources")
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
                description = "List of instance repositories associated with the project",
                nullable = true
        )
        List<InstanceRepositoryDto> instanceRepositories,

        @Schema(
                description = "Template repository configuration",
                nullable = true
        )
        TemplateRepositoryDto templateRepository,

        @Schema(
                description = "List of git group URLs for the project, each with a region and URL",
                nullable = true
        )
        List<GitGroupUrlDto> gitGroupUrls) {
}
