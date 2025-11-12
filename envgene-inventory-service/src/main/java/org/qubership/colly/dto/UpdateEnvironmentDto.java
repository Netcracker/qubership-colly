package org.qubership.colly.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.qubership.colly.db.data.EnvironmentStatus;
import org.qubership.colly.db.data.EnvironmentType;

import java.util.List;
import java.util.Optional;

/**
 * DTO for partial updates of Environment via PATCH endpoint.
 * Fields are wrapped in Optional to support partial updates:
 * - Optional.empty() = field not provided OR field is null (don't update)
 * - Optional.of(value) = field should be updated to this value
 *
 * Special handling for expirationDate:
 * - Optional.empty() = don't update
 * - Optional.of("") = clear the field (set to null)
 * - Optional.of("2025-12-31") = set to this date
 *
 * Note: Jackson cannot distinguish between absent fields and explicit null values.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateEnvironmentDto(
        Optional<String> description,
        Optional<List<String>> owners,
        Optional<List<String>> labels,
        Optional<List<String>> teams,
        Optional<EnvironmentStatus> status,
        Optional<String> expirationDate,
        Optional<EnvironmentType> type,
        Optional<String> role
) {
}
