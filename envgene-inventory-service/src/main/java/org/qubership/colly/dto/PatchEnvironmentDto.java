package org.qubership.colly.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.qubership.colly.db.data.EnvironmentStatus;
import org.qubership.colly.db.data.EnvironmentType;

import java.util.List;
import java.util.Optional;

/**
 * DTO for partial updates of Environment via PATCH endpoint.
 * Fields are wrapped in Optional to support partial updates:
 * - Optional.empty() = field not provided OR field is null (don't update)
 * - Optional.of(value) = field should be updated to this value
 * Special handling for expirationDate:
 * - Optional.empty() = don't update
 * - Optional.of("") = clear the field (set to null)
 * - Optional.of("2025-12-31") = set to this date
 * Note: Jackson cannot distinguish between absent fields and explicit null values.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        description = "Data transfer object for partial environment updates. Only provided fields will be updated. Omitted fields remain unchanged."
)
public record PatchEnvironmentDto(
        @Schema(
                description = "New description for the environment. Omit to keep current value, provide value to update.",
                examples = "Updated production environment for main application",
                nullable = true
        )
        Optional<String> description,

        @Schema(
                description = "New list of environment owners (replaces existing list). Omit to keep current owners.",
                examples = "[\"john.doe@company.com\", \"jane.smith@company.com\"]",
                nullable = true
        )
        Optional<List<String>> owners,

        @Schema(
                description = "New list of labels/tags (replaces existing list). Omit to keep current labels.",
                examples = "[\"production\", \"critical\", \"high-priority\"]",
                nullable = true
        )
        Optional<List<String>> labels,

        @Schema(
                description = "New list of teams with access (replaces existing list). Omit to keep current teams.",
                examples = "[\"QA\", \"DevOps\", \"Dev\"]",
                nullable = true
        )
        Optional<List<String>> teams,

        @Schema(
                description = "New status for the environment. Omit to keep current status.",
                enumeration = {"IN_USE", "RESERVED", "FREE", "MIGRATING"},
                examples = "IN_USE",
                nullable = true
        )
        Optional<EnvironmentStatus> status,

        @Schema(
                description = "New expiration date (ISO 8601 format: YYYY-MM-DD). Omit to keep current date. Provide empty string (\"\") to clear the date.",
                examples = "2025-12-31",
                nullable = true
        )
        Optional<String> expirationDate,

        @Schema(
                description = "New type/category for the environment. Omit to keep current type.",
                enumeration = {"ENVIRONMENT", "CSE_TOOLSET", "DESIGN_TIME", "APP_DEPLOYER", "INFRASTRUCTURE", "PORTAL", "UNDEFINED"},
                examples = "ENVIRONMENT",
                nullable = true
        )
        Optional<EnvironmentType> type,

        @Schema(
                description = "New role or purpose of the environment. Omit to keep current role.",
                examples = "production",
                nullable = true
        )
        Optional<String> role
) {
}
