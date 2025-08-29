package org.qubership.colly.db.data;

public enum DeploymentStatus {
    DEPLOYED("Deployed"),
    FAILED("Failed"),
    IN_PROGRESS("In Progress"),
    NOT_STARTED("Not Started");

    private final String displayName;

    DeploymentStatus(String displayName) {
        this.displayName = displayName;
    }

    public static DeploymentStatus fromString(String status) {
        for (DeploymentStatus envStatus : DeploymentStatus.values()) {
            if (envStatus.name().equalsIgnoreCase(status)) {
                return envStatus;
            }
        }
        return null;
    }

    public String getDisplayName() {
        return displayName;
    }
}
