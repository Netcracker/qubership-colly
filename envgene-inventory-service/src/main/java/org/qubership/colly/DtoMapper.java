package org.qubership.colly;

import jakarta.enterprise.context.ApplicationScoped;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.data.Namespace;
import org.qubership.colly.dto.*;

import java.util.List;

@ApplicationScoped
public class DtoMapper {

    public EnvironmentDto toDto(Environment environment) {
        return new EnvironmentDto(environment.getId(),
                environment.getName(),
                environment.getDescription(),
                environment.getNamespaces().stream().map(this::toDto).toList(),
                new LightClusterDto(environment.getClusterId(), environment.getClusterName()),
                environment.getOwners(),
                environment.getLabels(),
                environment.getTeams(),
                environment.getStatus(),
                environment.getExpirationDate(),
                environment.getType(),
                environment.getRole(),
                environment.getRegion()
        );
    }

    public NamespaceDto toDto(Namespace namespace) {
        return new NamespaceDto(namespace.getUid(), namespace.getName());
    }

    public InternalClusterInfoDto toDto(Cluster cluster) {
        return new InternalClusterInfoDto(cluster.getId(),
                cluster.getName(),
                cluster.getToken(),
                cluster.getCloudApiHost(),
                toLightDtos(cluster.getEnvironments()),
                cluster.getMonitoringUrl()
        );
    }

    public ClusterDto toClusterDto(Cluster cluster) {
        return new ClusterDto(cluster.getId(),
                cluster.getName(),
                cluster.getDashboardUrl(),
                cluster.getDbaasUrl(),
                cluster.getDeployerUrl());
    }

    private List<LightEnvironmentDto> toLightDtos(List<Environment> environments) {
        return environments.stream().map(this::toLightDto).toList();
    }

    public LightEnvironmentDto toLightDto(Environment environment) {
        return new LightEnvironmentDto(environment.getId(),
                environment.getName(),
                environment.getNamespaces().stream().map(this::toDto).toList());
    }

    public List<InternalClusterInfoDto> toClusterInfoDtos(List<? extends Cluster> clusters) {
        return clusters.stream().map(this::toDto).toList();
    }

    public List<EnvironmentDto> toDtos(List<? extends Environment> environments) {
        return environments.stream().map(this::toDto).toList();
    }

    public List<ClusterDto> toClusterDtos(List<Cluster> clusters) {
        return clusters.stream().map(this::toClusterDto).toList();
    }
}

