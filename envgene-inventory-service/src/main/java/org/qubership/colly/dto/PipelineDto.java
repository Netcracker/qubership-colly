package org.qubership.colly.dto;

import org.qubership.colly.projectrepo.PipelineType;

public record PipelineDto(PipelineType type, String url, String token, String region) {
}
