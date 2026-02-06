package org.qubership.colly.dto;

import org.qubership.colly.db.data.DeploymentItemType;
import org.qubership.colly.db.data.DeploymentMode;
import org.qubership.colly.db.data.DeploymentStatus;

public record DeploymentItemDto(String name, DeploymentStatus status, DeploymentItemType deploymentItemType,
                                DeploymentMode deploymentMode) {
}
