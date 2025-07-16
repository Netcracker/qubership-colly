package org.qubership.colly.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.data.Namespace;
import org.qubership.colly.dto.EnvironmentDTO;
import org.qubership.colly.dto.NamespaceDTO;

import java.util.List;

@ApplicationScoped
public class EnvironmentMapper {

    @Inject
    ClusterMapper clusterMapper;

    /**
     * Convert Environment entity to DTO
     */
    public EnvironmentDTO toDTO(Environment entity) {
        if (entity == null) {
            return null;
        }

        return new EnvironmentDTO(
                entity.id,
                entity.getName(),
                toNamespaceDTOs(entity.getNamespaces()),
                clusterMapper.toDTO(entity.getCluster()),
                entity.getOwner(),
                entity.getTeam(),
                entity.getStatus(),
                entity.getExpirationDate(),
                entity.getType(),
                entity.getLabels(),
                entity.getDescription(),
                entity.getDeploymentVersion(),
                entity.getCleanInstallationDate(),
                entity.getMonitoringData()
        );
    }

    /**
     * Convert a list of Environment entities to DTOs
     */
    public List<EnvironmentDTO> toDTOs(List<Environment> entities) {
        return entities.stream()
                .map(this::toDTO)
                .toList();
    }


    private List<NamespaceDTO> toNamespaceDTOs(List<Namespace> namespaces) {
        if (namespaces == null) {
            return List.of();
        }
        return namespaces.stream()
                .map(ns -> new NamespaceDTO(ns.name))
                .toList();
    }
}
