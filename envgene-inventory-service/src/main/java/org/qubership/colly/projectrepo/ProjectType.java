package org.qubership.colly.projectrepo;

public enum ProjectType {
    PRODUCT,
    PROJECT;

    public static ProjectType fromString(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Project type cannot be null or blank");
        }
        return ProjectType.valueOf(type.toUpperCase());
    }
}
