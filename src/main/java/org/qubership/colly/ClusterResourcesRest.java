package org.qubership.colly;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.qubership.colly.db.Cluster;
import org.qubership.colly.db.Environment;

import java.util.List;

@Path("/clusters")
public class ClusterResourcesRest {
    @Inject
    CollyStorage collyStorage;


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public List<Cluster> getClusters() {
        return collyStorage.getClusters();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/environments")
    public List<Environment> getEnvironments() {
        return collyStorage.getEnvironments();
    }

    @POST
    @Path("/tick")
    @Produces(MediaType.APPLICATION_JSON)
    public void loadEnvironmentsManually() {
        collyStorage.executeTask();
    }

    @GET
    @Path("/db")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Cluster> loadEClustersFromDB() {
        return collyStorage.getClustersFromDb();
    }


}

