package org.qubership.colly;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.qubership.colly.cloudpassport.CloudPassport;

import java.util.List;

@RegisterRestClient(configKey = "envgene-inventory-service")
@Path("/colly/v2/inventory-service")
public interface EnvgeneInventoryServiceRest {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/clusters")
    List<CloudPassport> getCloudPassports();

}
