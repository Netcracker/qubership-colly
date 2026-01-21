package org.qubership.colly.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema(description = "Kubernetes cluster information")
public record ClusterDto(
        @Schema(
                description = "Unique identifier of the cluster (UUID format)",
                examples = "995f5292-5725-42b6-ad28-0e8629e0f791",
                required = true
        )
        String id,

        @Schema(
                description = "Name of the cluster",
                examples = "prod-cluster-01",
                required = true
        )
        String name,

        @Schema(
                description = "List of environments deployed on this cluster",
                nullable = true
        )
        List<LightEnvironmentDto> environments,

        @Schema(
                description = "URL to the Kubernetes dashboard for this cluster",
                examples = "https://dashboard.k8s.example.com",
                nullable = true
        )
        String dashboardUrl,

        @Schema(
                description = "DBaaS URL for this cluster",
                examples = "https://dbaas.example.com",
                nullable = true
        )
        String dbaasUrl,

        @Schema(
                description = "Deployer URL for this cluster",
                examples = "https://deployer.example.com",
                nullable = true
        )
        String deployerUrl,

        @Schema(
                description = "ArgoCD URL for GitOps deployments",
                examples = "https://argocd.example.com",
                nullable = true
        )
        String argoUrl
) {
}

