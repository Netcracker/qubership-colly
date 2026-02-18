package org.qubership.colly.dto;

import java.time.Instant;
import java.util.List;

public record DeploymentOperationDto(Instant completedAt, List<DeploymentItemDto> deploymentItems) {
}
