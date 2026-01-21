package org.qubership.colly.uiservice.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.qubership.colly.uiservice.dto.ClusterDto;
import org.qubership.colly.uiservice.dto.EnvironmentDto;
import org.qubership.colly.uiservice.dto.LightClusterDto;
import org.qubership.colly.uiservice.dto.NamespaceDto;
import org.qubership.colly.uiservice.dto.inventory.InventoryClusterDto;
import org.qubership.colly.uiservice.dto.inventory.InventoryEnvironmentDto;
import org.qubership.colly.uiservice.dto.inventory.InventoryNamespaceDto;
import org.qubership.colly.uiservice.dto.operational.OperationalClusterDto;
import org.qubership.colly.uiservice.dto.operational.OperationalEnvironmentDto;
import org.qubership.colly.uiservice.dto.operational.OperationalNamespaceDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mapper for merging inventory and operational environment DTOs into aggregated DTO
 */
@ApplicationScoped
public class DtoMapper {

    /**
     * Merge inventory and operational environment DTOs into one aggregated DTO
     *
     * @param inventory   Environment data from inventory-service (metadata)
     * @param operational Environment data from operational-service (runtime data), may be null
     * @return Merged EnvironmentDto
     */
    public EnvironmentDto merge(InventoryEnvironmentDto inventory, OperationalEnvironmentDto operational) {
        // Merge namespaces: add existsInK8s from operational
        List<NamespaceDto> namespaces = mergeNamespaces(
                inventory.namespaces(),
                operational != null ? operational.namespaces() : null
        );

        // Merge cluster: add synced from operational
        LightClusterDto cluster = new LightClusterDto(inventory.cluster().id(), inventory.cluster().name());

        return new EnvironmentDto(
                // From inventory
                inventory.id(),
                inventory.name(),
                inventory.description(),
                namespaces,
                cluster,
                inventory.owners(),
                inventory.labels(),
                inventory.teams(),
                inventory.status(),
                inventory.expirationDate(),
                inventory.type(),
                inventory.role(),
                inventory.region(),
                inventory.accessGroups(),
                inventory.effectiveAccessGroups(),

                // From operational (may be null)
                operational != null ? operational.deploymentVersion() : null,
                operational != null ? operational.cleanInstallationDate() : null,
                operational != null ? operational.monitoringData() : null
        );
    }

    /**
     * Merge namespaces from inventory and operational
     */
    private List<NamespaceDto> mergeNamespaces(List<InventoryNamespaceDto> inventoryNamespaces, List<OperationalNamespaceDto> operationalNamespaces) {

        if (inventoryNamespaces == null) {
            return List.of();
        }

        // Create a map of operational namespaces by id for quick lookup
        Map<String, OperationalNamespaceDto> operationalById = new HashMap<>();
        if (operationalNamespaces != null) {
            operationalById = operationalNamespaces.stream()
                    .filter(ns -> ns.id() != null)
                    .collect(Collectors.toMap(
                            OperationalNamespaceDto::id,
                            ns -> ns,
                            (existing, replacement) -> existing
                    ));
        }

        Map<String, OperationalNamespaceDto> finalOperationalById = operationalById;
        return inventoryNamespaces.stream()
                .map(invNs -> {
                    OperationalNamespaceDto opNs = finalOperationalById.get(invNs.id());
                    Boolean existsInK8s = opNs != null ? opNs.existsInK8s() : null;

                    return new NamespaceDto(
                            invNs.id(),
                            invNs.name(),
                            existsInK8s
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Merge cluster from inventory and operational
     */
    public ClusterDto mergeCluster(InventoryClusterDto inventoryCluster, OperationalClusterDto operationalCluster) {

        if (inventoryCluster == null) {
            return null;
        }

        boolean synced = operationalCluster != null && operationalCluster.synced();

        return new ClusterDto(
                inventoryCluster.id(),
                inventoryCluster.name(),
                synced,
                inventoryCluster.dashboardUrl(),
                inventoryCluster.dbaasUrl(),
                inventoryCluster.deployerUrl(),
                inventoryCluster.argoUrl()
        );
    }


}
