package org.qubership.colly;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.qubership.colly.cloudpassport.CloudPassport;

import java.util.List;

@RegisterRestClient(configKey = "envgene-inventory-service")
@Path("/colly/envgene-inventory-service")
public interface EnvgeneInventoryServiceRest {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/clusters")
    List<CloudPassport> getCloudPassports();

}
