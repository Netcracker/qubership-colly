package org.qubership.colly.achka;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@Path("/v2/public")
@RegisterRestClient(configKey = "achka-api")
public interface AchKubernetesAgentClient {

    @GET
    @Path("/versions")
    @Produces(MediaType.APPLICATION_JSON)
    AchkaResponse versions(@QueryParam("namespace") String namespace,
                           @QueryParam("group_by") String groupBy);

    record AchkaResponse(List<ApplicationsVersion> versions) {
    }
}
