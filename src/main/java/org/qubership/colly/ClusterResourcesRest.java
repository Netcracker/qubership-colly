package org.qubership.colly;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.dto.ApplicationMetadata;
import org.qubership.colly.monitoring.MonitoringService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/colly")
public class ClusterResourcesRest {
    @Inject
    CollyStorage collyStorage;

    @Inject
    SecurityIdentity securityIdentity;
    @Inject
    MonitoringService monitoringService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/clusters")
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

    @POST
    @Path("/environments/{envId}")
    @RolesAllowed("admin")
    public void saveEnvironment(@PathParam("envId") String id,
                                @FormParam("name") String name,
                                @FormParam("owner") String owner,
                                @FormParam("description") String description,
                                @FormParam("status") String status,
                                @FormParam("labels") List<String> labels,
                                @FormParam("type") String type,
                                @FormParam("team") String team,
                                @FormParam("expirationDate") String expirationDate) {
        LocalDate date = null;
        if (expirationDate != null && !expirationDate.isEmpty()) {
            date = LocalDate.parse(expirationDate);
        }
        collyStorage.saveEnvironment(id, name, owner, description, status, labels, type, team, date);
    }

    @POST
    @Path("/clusters/{clusterName}")
    @RolesAllowed("admin")
    public void saveCluster(@PathParam("clusterName") String clusterName,
                            @FormParam("description") String description) {
        collyStorage.saveCluster(clusterName, description);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/auth-status")
    public Response getAuthStatus() {
        if (securityIdentity.isAnonymous()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("authenticated", false))
                    .build();
        }

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("authenticated", true);
        userInfo.put("username", securityIdentity.getPrincipal().getName());
        userInfo.put("isAdmin", securityIdentity.hasRole("admin"));
        return Response.ok(userInfo).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/metadata")
    public ApplicationMetadata getMetadata() {
        List<String> parameters = monitoringService.getParameters();
        return new ApplicationMetadata(parameters);
    }
}

