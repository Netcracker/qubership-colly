package org.qubership.colly.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.qubership.colly.db.data.DeploymentItem;
import org.qubership.colly.db.data.DeploymentOperation;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.repository.ClusterRepository;
import org.qubership.colly.db.repository.NamespaceRepository;
import org.qubership.colly.dto.DeploymentItemDto;
import org.qubership.colly.dto.DeploymentOperationDto;
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
                clusterMapper.toDTO(clusterRepository.findById(entity.getClusterId())),
                entity.getDeploymentVersion(),
                entity.getCleanInstallationDate(),
                toDeploymentOperationDtos(entity.getDeploymentOperations()),
                entity.getMonitoringData()
        );
    }


    private List<NamespaceDTO> toNamespaceDTOs(List<String> namespaceIds) {
        if (namespaceIds == null) {
            return List.of();
        }
        List<NamespaceDTO> namespaceDTOs = new ArrayList<>();
        for (String nsId : namespaceIds) {
            namespaceRepository.findByUid(nsId).ifPresent(ns ->
                    namespaceDTOs.add(new NamespaceDTO(ns.getId(), ns.getName(), ns.getExistsInK8s()))
            );
        }
        return namespaceDTOs;
    }

    private List<DeploymentOperationDto> toDeploymentOperationDtos(List<DeploymentOperation> deploymentOperations) {
        if (deploymentOperations == null) {
            return List.of();
        }
        return deploymentOperations.stream().map(this::toDTO).toList();
    }

    public DeploymentOperationDto toDTO(DeploymentOperation entity) {
        if (entity == null) {
            return null;
        }
        return new DeploymentOperationDto(entity.createdAt(), entity.deploymentItems().stream().map(this::toDTO).toList());
    }

    public DeploymentItemDto toDTO(DeploymentItem deploymentItem) {
        if (deploymentItem == null) {
            return null;
        }
        return new DeploymentItemDto(deploymentItem.name(), deploymentItem.status(), deploymentItem.deploymentItemType(), deploymentItem.deploymentMode());
    }
}
