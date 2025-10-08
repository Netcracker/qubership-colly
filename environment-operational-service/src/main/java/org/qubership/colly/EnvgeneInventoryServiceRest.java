package org.qubership.colly;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.qubership.colly.cloudpassport.CloudPassport;
import org.qubership.colly.dto.EnvironmentDTO;

import java.util.List;

@RegisterRestClient(configKey = "envgene-inventory-service")
@Path("/colly/envgene-inventory-service")
public interface EnvgeneInventoryServiceRest {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/clusters")
    List<CloudPassport> getCloudPassports();

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/clusters/{clusterName}/environments/{environmentName}")
    void updateEnvironment(@PathParam("clusterName") String clusterName,
                           @PathParam("environmentName") String environmentName,
                           EnvironmentDTO environment);

}
