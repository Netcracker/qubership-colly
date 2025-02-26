package org.qubership.colly;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.qubership.colly.data.Cluster;
import org.qubership.colly.data.Environment;

import java.util.List;

@Path("/clusters")
public class ClusterResourcesRest {
    @Inject
    CollyStorage collyStorage;
    @Inject
    ClusterResourcesLoader clusterResourcesLoader;

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

    @GET
    @Path("/tick")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Cluster> loadEnvironmentsManually() {
        return clusterResourcesLoader.loadClusters();
    }
}

