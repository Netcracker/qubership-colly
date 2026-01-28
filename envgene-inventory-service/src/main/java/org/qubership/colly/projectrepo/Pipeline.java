package org.qubership.colly.projectrepo;

public record Pipeline(PipelineType type, String url, String branch, String token, String region) {
}
