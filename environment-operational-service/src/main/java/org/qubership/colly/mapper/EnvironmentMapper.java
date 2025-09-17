package org.qubership.colly.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.data.Namespace;
import org.qubership.colly.db.repository.ClusterRepository;
import org.qubership.colly.db.repository.NamespaceRepository;
import org.qubership.colly.dto.EnvironmentDTO;
import org.qubership.colly.dto.NamespaceDTO;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class EnvironmentMapper {

    private final ClusterMapper clusterMapper;
    private final NamespaceRepository namespaceRepository;
    private final ClusterRepository clusterRepository;

    @Inject
    public EnvironmentMapper(ClusterMapper clusterMapper, NamespaceRepository namespaceRepository, ClusterRepository clusterRepository) {
        this.clusterMapper = clusterMapper;
        this.namespaceRepository = namespaceRepository;
        this.clusterRepository = clusterRepository;
    }

    /**
     * Convert Environment entity to DTO
     */
    public EnvironmentDTO toDTO(Environment entity) {
        if (entity == null) {
            return null;
        }

        return new EnvironmentDTO(
                entity.getId(),
                entity.getName(),
                toNamespaceDTOs(entity.getNamespaceIds()),
                clusterMapper.toDTO(clusterRepository.findByName(entity.getClusterId()).orElse(null)),
                entity.getOwner(),
                entity.getTeam(),
                entity.getStatus(),
                entity.getExpirationDate(),
                entity.getType(),
                entity.getLabels(),
                entity.getDescription(),
                entity.getDeploymentVersion(),
                entity.getCleanInstallationDate(),
                entity.getMonitoringData(),
                entity.getDeploymentStatus(),
                entity.getTicketLinks()
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


    private List<NamespaceDTO> toNamespaceDTOs(List<String> namespaceIds) {
        if (namespaceIds == null) {
            return List.of();
        }
        List<NamespaceDTO> namespaceDTOs = new ArrayList<>();
        for (String nsId : namespaceIds) {
            namespaceRepository.findByUid(nsId).ifPresent(ns -> 
                namespaceDTOs.add(new NamespaceDTO(ns.getUid(), ns.getName(), ns.getExistsInK8s()))
            );
        }
        return namespaceDTOs;
    }
}
