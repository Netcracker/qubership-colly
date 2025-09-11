package org.qubership.colly.db.data;

public enum EnvironmentStatus {
    IN_USE("In Use"),
    RESERVED("Reserved"),
    FREE("Free"),
    MIGRATING("Migrating");

    private final String displayName;

    EnvironmentStatus(String displayName) {
        this.displayName = displayName;
    }

    public static EnvironmentStatus fromString(String status) {
        for (EnvironmentStatus envStatus : EnvironmentStatus.values()) {
            if (envStatus.name().equalsIgnoreCase(status)) {
                return envStatus;
            }
        }
        return FREE;
    }

    public String getDisplayName() {
        return displayName;
    }
}
