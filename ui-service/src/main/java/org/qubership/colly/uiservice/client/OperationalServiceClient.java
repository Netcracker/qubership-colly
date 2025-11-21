package org.qubership.colly.uiservice.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.qubership.colly.uiservice.dto.operational.OperationalClusterDto;
import org.qubership.colly.uiservice.dto.operational.OperationalEnvironmentDto;

import java.util.List;
import java.util.Map;

@RegisterRestClient(configKey = "operational-service")
@RegisterClientHeaders(OidcClientHeadersFactory.class)
@Path("/colly/v2/operational-service")
public interface OperationalServiceClient {

    @GET
    @Path("/auth-status")
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, Object> getAuthStatus();

    @GET
    @Path("/metadata")
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, Object> getMetadata();

    @GET
    @Path("/clusters")
    @Produces(MediaType.APPLICATION_JSON)
    List<OperationalClusterDto> getClusters();


    @GET
    @Path("/environments")
    @Produces(MediaType.APPLICATION_JSON)
    List<OperationalEnvironmentDto> getEnvironments();

}
