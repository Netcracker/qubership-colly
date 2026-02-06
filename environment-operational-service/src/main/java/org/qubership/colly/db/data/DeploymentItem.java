package org.qubership.colly.db.data;

public record DeploymentItem(String name, DeploymentStatus status, DeploymentItemType deploymentItemType,
                             DeploymentMode deploymentMode) {
}
