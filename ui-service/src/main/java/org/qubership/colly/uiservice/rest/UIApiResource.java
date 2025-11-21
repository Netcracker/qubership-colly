package org.qubership.colly.uiservice.rest;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.qubership.colly.uiservice.aggregator.DataAggregatorService;
import org.qubership.colly.uiservice.client.InventoryServiceClient;
import org.qubership.colly.uiservice.client.OperationalServiceClient;
import org.qubership.colly.uiservice.dto.ClusterDto;
import org.qubership.colly.uiservice.dto.EnvironmentDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for UI - proxy for backend services with data aggregation
 */
@Path("/colly/v2/ui-service")
@Produces(MediaType.APPLICATION_JSON)
public class UIApiResource {

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    DataAggregatorService aggregatorService;

    @Inject
    @RestClient
    InventoryServiceClient inventoryServiceClient;

    @Inject
    @RestClient
    OperationalServiceClient operationalServiceClient;

    // ========== Auth & Metadata ==========

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/auth-status")
    public Response getAuthStatus() {
        if (securityIdentity.isAnonymous()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("authenticated", false))
                    .build();
        }

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("authenticated", true);
        userInfo.put("username", securityIdentity.getPrincipal().getName());
        userInfo.put("isAdmin", securityIdentity.hasRole("admin"));
        return Response.ok(userInfo).build();
    }

    @GET
    @Path("/metadata")
    public Response getMetadata() {
        try {
            Map<String, Object> metadata = operationalServiceClient.getMetadata();
            return Response.ok(metadata).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to fetch metadata: " + e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/environments")
    public List<EnvironmentDto> getEnvironments() {
        return aggregatorService.getAggregatedEnvironments();
    }

    @PATCH
    @Path("/environments/{environmentId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> updateEnvironment(@PathParam("environmentId") String environmentId, Map<String, Object> environmentData) {
        return inventoryServiceClient.updateEnvironment(environmentId, environmentData);
    }

    // ========== Clusters ==========

    @GET
    @Path("/clusters")
    public List<ClusterDto> getClusters() {
        return aggregatorService.getClusters();
    }

    // ========== Health ==========

    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(Map.of("status", "UP", "service", "ui-service")).build();
    }
}
