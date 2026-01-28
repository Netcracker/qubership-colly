package org.qubership.colly.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.qubership.colly.projectrepo.PipelineType;

@Schema(description = "Pipelines configuration")
public record PipelineDto(
        @Schema(
                description = "Type of the pipeline",
                enumeration = {"CLUSTER_PROVISION", "ENV_PROVISION", "SOLUTION_DEPLOY"},
                required = true
        )
        PipelineType type,

        @Schema(
                description = "Pipeline URL (e.g., GitLab pipeline URL)",
                examples = "https://gitlab.com/organization/project/-/pipelines",
                required = true
        )
        String url,
        @Schema(
                description = "Git branch to use",
                examples = "main",
                nullable = true
        )
        String branch,

        @Schema(
                description = "Access token for pipeline authentication",
                examples = "glpat-1234567890abcdef",
                nullable = true
        )
        String token,

        @Schema(
                description = "Geographic region or datacenter location",
                examples = "us-east-1",
                nullable = true
        )
        String region) {
}
