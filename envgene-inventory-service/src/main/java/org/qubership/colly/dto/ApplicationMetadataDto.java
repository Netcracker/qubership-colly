package org.qubership.colly.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Application metadata including sync info")
public record ApplicationMetadataDto(
        @Schema(
                description = "Cron expression defining the schedule for synchronization with Projects Git",
                examples = "0 * * * * ?",
                required = true
        )
        String syncSchedule
) {
}
