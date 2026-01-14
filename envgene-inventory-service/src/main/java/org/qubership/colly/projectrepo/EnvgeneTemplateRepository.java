package org.qubership.colly.projectrepo;

public record EnvgeneTemplateRepository(String id, String url, String token, String branch,
                                        EnvgeneArtifact envgeneArtifact) {
}
