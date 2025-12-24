package org.qubership.colly.projectrepo;

import java.util.Arrays;

public enum PipelineType {
    CLUSTER_PROVISION("clusterProvision"),
    ENV_PROVISION("envProvision"),
    SOLUTION_DEPLOY("solutionDeploy");

    private final String value;

    PipelineType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static PipelineType fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Project type cannot be null or blank");
        }

        return Arrays.stream(values())
                .filter(type -> type.value.equals(value))
                .findFirst()
                .orElse(null);
    }

    @Override
    public String toString() {
        return value;
    }
}
