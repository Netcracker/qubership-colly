package org.qubership.colly.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.qubership.colly.projectrepo.EnvgeneArtifact;

@Schema(description = "Template repository configuration for Envgene templates")
public record TemplateRepositoryDto(
        @Schema(
                description = "Unique identifier of the template repository",
                examples = "template-repo-123",
                required = true
        )
        String id,

        @Schema(
                description = "Repository URL",
                examples = "https://github.com/organization/templates.git",
                required = true
        )
        String url,

        @Schema(
                description = "Access token for repository authentication",
                examples = "ghp_1234567890abcdef",
                nullable = true
        )
        String token,

        @Schema(
                description = "Git branch to use",
                examples = "main",
                nullable = true
        )
        String branch,

        @Schema(
                description = "Envgene artifact configuration",
                nullable = true
        )
        EnvgeneArtifact envgeneArtifact) {
}
