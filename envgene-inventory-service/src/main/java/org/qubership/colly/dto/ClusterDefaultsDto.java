package org.qubership.colly.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema(description = "Default parameters for a cluster")
public record ClusterDefaultsDto(List<String> owners, List<String> roAdGroups, List<String> rwAdGroups) {
}
