package org.qubership.colly.projectrepo;

public record Pipeline(PipelineType type, String url, String token, String region) {
}
