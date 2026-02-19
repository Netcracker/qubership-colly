package org.qubership.colly.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Application metadata including sync info")
public record ApplicationMetadataDto(
        @Schema(
                description = "Cron expression defining the schedule for synchronization with Projects Git",
                examples = "0 * * * * ?",
                required = true
        )
        String syncSchedule,
        @Schema(
                description = "Timestamp of the last project repository synchronization",
                examples = "2025-01-15T10:30:00Z"
        )
        Instant lastProjectSyncAt
) {
}
