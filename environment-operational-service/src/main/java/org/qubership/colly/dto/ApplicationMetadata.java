package org.qubership.colly.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema(description = "Application metadata including available monitoring parameters")
public record ApplicationMetadata(
        @Schema(
                description = "List of available monitoring column names that can be displayed in the UI",
                examples = "[\"cpu_usage\", \"memory_usage\", \"pod_count\", \"deployment_version\"]",
                required = true
        )
        List<String> monitoringColumns,
        @Schema(
                description = "",
                examples = "0 * * * * ?",
                required = true
        )
        String clusterSyncSchedule
) {
}
