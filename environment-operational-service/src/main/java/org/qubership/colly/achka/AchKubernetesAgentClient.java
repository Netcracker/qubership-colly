package org.qubership.colly.achka;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;
import java.util.Map;

@Path("/v2/public")
@RegisterRestClient(configKey = "achka-api")
public interface AchKubernetesAgentClient {

    @GET
    @Path("/versions")
    @Produces(MediaType.APPLICATION_JSON)
    AchkaResponse versions(@QueryParam("namespace") List<String> namespaces,
                           @QueryParam("group_by") String groupBy);

    record AchkaResponse(
            @JsonProperty("versions")
            Map<String, List<ApplicationsVersion>> deploymentSessionIdToApplicationVersions) {
    }
}
