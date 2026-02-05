package org.qubership.colly.db.data;

import java.time.Instant;
import java.util.List;

public record DeploymentOperation(Instant createdAt, List<DeploymentItem> deploymentItems) {
}
