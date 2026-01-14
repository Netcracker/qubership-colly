package org.qubership.colly.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.qubership.colly.db.data.EnvironmentStatus;
import org.qubership.colly.db.data.EnvironmentType;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Environment data transfer object containing all environment details")
public record EnvironmentDto(
        @Schema(
                description = "Unique identifier of the environment (UUID format)",
                examples = "96180fe7-f025-465f-bbbf-5e83f301a615",
                required = true
        )
        String id,

        @Schema(
                description = "Name of the environment",
                examples = "prod-env-1",
                required = true
        )
        String name,

        @Schema(
                description = "Detailed description of the environment",
                examples = "Production environment for main application",
                nullable = true
        )
        String description,

        @Schema(
                description = "List of Kubernetes namespaces associated with this environment",
                required = true
        )
        List<NamespaceDto> namespaces,

        @Schema(
                description = "Kubernetes cluster where this environment is deployed",
                required = true
        )
        LightClusterDto cluster,

        @Schema(
                description = "List of environment owners",
                examples = "[\"john.doe\", \"jane.smith\"]"
        )
        List<String> owners,

        @Schema(
                description = "Custom labels/tags for categorization and filtering",
                examples = "[\"production\", \"critical\", \"high-availability\"]"
        )
        List<String> labels,

        @Schema(
                description = "Teams that have access to this environment",
                examples = "[\"Platform\", \"DevOps\", \"SRE\"]"
        )
        List<String> teams,

        @Schema(
                description = "Current status of the environment",
                enumeration = {"IN_USE", "RESERVED", "FREE", "MIGRATING"},
                examples = "FREE",
                required = true
        )
        EnvironmentStatus status,

        @Schema(
                description = "Expiration date after which the environment should be cleaned up or reviewed (ISO 8601 format)",
                examples = "2025-12-31",
                nullable = true
        )
        LocalDate expirationDate,

        @Schema(
                description = "Type/category of the environment",
                enumeration = {"ENVIRONMENT", "CSE_TOOLSET", "DESIGN_TIME", "APP_DEPLOYER", "INFRASTRUCTURE", "PORTAL", "UNDEFINED"},
                examples = "ENVIRONMENT",
                required = true
        )
        EnvironmentType type,

        @Schema(
                description = "Role or purpose of the environment in the infrastructure",
                examples = "production",
                nullable = true
        )
        String role,

        @Schema(
                description = "Geographic region or datacenter location",
                examples = "us-east-1",
                nullable = true
        )
        String region
) {
}
