package org.qubership.colly.uiservice.client;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.qubership.colly.uiservice.dto.inventory.InventoryClusterDto;
import org.qubership.colly.uiservice.dto.inventory.InventoryEnvironmentDto;

import java.util.List;
import java.util.Map;

@RegisterRestClient(configKey = "inventory-service")
@Path("/colly/v2/inventory-service")
public interface InventoryServiceClient {

    @GET
    @Path("/environments")
    @Produces(MediaType.APPLICATION_JSON)
    List<InventoryEnvironmentDto> getEnvironments();

    @PATCH
    @Path("/environments/{environmentId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, Object> updateEnvironment(@PathParam("environmentId") String environmentId, Map<String, Object> environmentData);


    @GET
    @Path("/clusters")
    @Produces(MediaType.APPLICATION_JSON)
    List<InventoryClusterDto> getClusters();
}
