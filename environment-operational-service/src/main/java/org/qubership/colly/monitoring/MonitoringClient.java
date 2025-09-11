package org.qubership.colly.monitoring;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/v1")
@RegisterRestClient(configKey = "monitoring-api")
public interface MonitoringClient {

    @GET
    @Path("/query")
    @Produces(MediaType.APPLICATION_JSON)
    MonitoringResponse executeQuery(@QueryParam("query") String query);

}
