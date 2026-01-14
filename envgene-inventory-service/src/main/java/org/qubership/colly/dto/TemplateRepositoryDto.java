package org.qubership.colly.dto;

import org.qubership.colly.projectrepo.EnvgeneArtifact;

public record TemplateRepositoryDto(String id, String url, String token, String branch, EnvgeneArtifact envgeneArtifact) {
}
