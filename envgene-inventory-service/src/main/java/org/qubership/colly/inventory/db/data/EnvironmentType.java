package org.qubership.colly.inventory.db.data;

public enum EnvironmentType {
    ENVIRONMENT("Environment"),
    CSE_TOOLSET("CSE Toolset"),
    DESIGN_TIME("Design Time"),
    APP_DEPLOYER("App Deployer"),
    INFRASTRUCTURE("Infrastructure"),
    PORTAL("Portal"),
    UNDEFINED("Undefined")
    ;

    private final String displayName;

    EnvironmentType(String displayName) {
        this.displayName = displayName;
    }

    public static EnvironmentType fromString(String envType) {
        for (EnvironmentType type : EnvironmentType.values()) {
            if (type.name().equalsIgnoreCase(envType)) {
                return type;
            }
        }
        return ENVIRONMENT;
    }

    public String getDisplayName() {
        return displayName;
    }
}
