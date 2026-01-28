package org.qubership.colly.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Instance repository configuration containing repository access details")
public record InstanceRepositoryDto(
        @Schema(
                description = "Repository URL",
                examples = "https://github.com/organization/instance-repo.git",
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
                description = "Access token for repository authentication",
                examples = "ghp_1234567890abcdef",
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
