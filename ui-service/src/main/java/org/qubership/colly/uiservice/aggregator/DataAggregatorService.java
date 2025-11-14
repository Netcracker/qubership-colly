package org.qubership.colly.uiservice.aggregator;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.qubership.colly.uiservice.client.InventoryServiceClient;
import org.qubership.colly.uiservice.client.OperationalServiceClient;
import org.qubership.colly.uiservice.dto.ClusterDto;
import org.qubership.colly.uiservice.dto.EnvironmentDto;
import org.qubership.colly.uiservice.dto.inventory.InventoryClusterDto;
import org.qubership.colly.uiservice.dto.inventory.InventoryEnvironmentDto;
import org.qubership.colly.uiservice.dto.operational.OperationalClusterDto;
import org.qubership.colly.uiservice.dto.operational.OperationalEnvironmentDto;
import org.qubership.colly.uiservice.mapper.DtoMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for aggregating data from Inventory and Operational services
 */
@ApplicationScoped
public class DataAggregatorService {

    @Inject
    @RestClient
    InventoryServiceClient inventoryServiceClient;

    @Inject
    @RestClient
    OperationalServiceClient operationalServiceClient;

    @Inject
    DtoMapper mapper;

    /**
     * Get aggregated environments list combining data from inventory and operational services.
     * Inventory service provides metadata (description, owners, status, etc.)
     * Operational service provides runtime data (resources, monitoring, etc.)
     */
    public List<EnvironmentDto> getAggregatedEnvironments() {
        Log.info("Aggregating environments from inventory and operational services");

        List<InventoryEnvironmentDto> inventoryEnvironments = new ArrayList<>();
        try {
            inventoryEnvironments = inventoryServiceClient.getEnvironments();
            Log.info("Fetched " + inventoryEnvironments.size() + " environments from inventory service");
        } catch (Exception e) {
            Log.error("Failed to fetch environments from inventory service", e);
        }

        List<OperationalEnvironmentDto> operationalEnvironments = new ArrayList<>();
        try {
            operationalEnvironments = operationalServiceClient.getEnvironments();
            Log.info("Fetched " + operationalEnvironments.size() + " environments from operational service");
        } catch (Exception e) {
            Log.error("Failed to fetch environments from operational service", e);
        }

        Map<String, OperationalEnvironmentDto> operationalById = operationalEnvironments.stream()
                .collect(Collectors.toMap(
                        OperationalEnvironmentDto::id,
                        env -> env,
                        (existing, replacement) -> existing
                ));

        List<EnvironmentDto> mergedEnvironments = inventoryEnvironments.stream()
                .map(invEnv -> {
                    OperationalEnvironmentDto opEnv = operationalById.get(invEnv.id());
                    return mapper.merge(invEnv, opEnv);
                })
                .collect(Collectors.toList());

        Log.info("Aggregated " + mergedEnvironments.size() + " environments");
        return mergedEnvironments;
    }

    public List<ClusterDto> getClusters() {
        Log.info("Aggregating clusters from inventory and operational services");

        List<InventoryClusterDto> inventoryClusters = new ArrayList<>();
        try {
            inventoryClusters = inventoryServiceClient.getClusters();
            Log.info("Fetched " + inventoryClusters.size() + " clusters from inventory service");
        } catch (Exception e) {
            Log.error("Failed to fetch clusters from inventory service", e);
        }

        List<OperationalClusterDto> operationalClusters = new ArrayList<>();
        try {
            operationalClusters = operationalServiceClient.getClusters();
            Log.info("Fetched " + operationalClusters.size() + " clusters from operational service");
        } catch (Exception e) {
            Log.error("Failed to fetch clusters from operational service", e);
        }

        Map<String, OperationalClusterDto> operationalById = operationalClusters.stream()
                .collect(Collectors.toMap(
                        OperationalClusterDto::id,
                        cluster -> cluster,
                        (existing, replacement) -> existing
                ));

        List<ClusterDto> mergedClusters = inventoryClusters.stream()
                .map(invCluster -> {
                    OperationalClusterDto opCluster = operationalById.get(invCluster.id());
                    return mapper.mergeCluster(invCluster, opCluster);
                })
                .collect(Collectors.toList());

        Log.info("Aggregated " + mergedClusters.size() + " clusters");
        return mergedClusters;

    }
}
