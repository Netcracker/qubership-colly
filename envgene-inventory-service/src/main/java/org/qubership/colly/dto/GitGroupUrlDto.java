package org.qubership.colly.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Git group URL entry with region")
public record GitGroupUrlDto(
        @Schema(description = "Region identifier", nullable = true)
        String region,
        @Schema(description = "URL of the git group", examples = "https://gitlab.com/my-project-git-group")
        String url) {
}
