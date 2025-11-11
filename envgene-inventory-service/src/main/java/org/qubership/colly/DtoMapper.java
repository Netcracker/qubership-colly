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
                new ClusterDto(environment.getId(), environment.getName()),
                environment.getOwners(),
                environment.getLabels(),
                environment.getTeams(),
                environment.getStatus(),
                environment.getExpirationDate(),
                environment.getType(),
                environment.getRole()
        );
    }

    public NamespaceDto toDto(Namespace namespace) {
        return new NamespaceDto(namespace.getUid(), namespace.getName());
    }

    public CloudPassportDto toDto(Cluster cluster) {
        return new CloudPassportDto(cluster.getId(),
                cluster.getName(),
                cluster.getToken(),
                cluster.getCloudApiHost(),
                toLightDtos(cluster.getEnvironments()),
                cluster.getMonitoringUrl()
        );
    }

    private List<LightEnvironmentDto> toLightDtos(List<Environment> environments) {
        return environments.stream().map(this::toLightDto).toList();
    }

    public LightEnvironmentDto toLightDto(Environment environment) {
        return new LightEnvironmentDto(environment.getId(),
                environment.getName(),
                environment.getNamespaces().stream().map(this::toDto).toList());
    }

    public List<CloudPassportDto> toCloudPassportDtos(List<? extends Cluster> clusters) {
        return clusters.stream().map(this::toDto).toList();
    }

    public List<EnvironmentDto> toDtos(List<? extends Environment> environments) {
        return environments.stream().map(this::toDto).toList();
    }
}

