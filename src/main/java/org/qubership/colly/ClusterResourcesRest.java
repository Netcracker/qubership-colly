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
import org.qubership.colly.dto.EnvironmentDTO;
import org.qubership.colly.dto.ClusterDTO;
import org.qubership.colly.mapper.EnvironmentMapper;
import org.qubership.colly.mapper.ClusterMapper;
import org.qubership.colly.monitoring.MonitoringService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/colly")
public class ClusterResourcesRest {
    
    private final CollyStorage collyStorage;
    private final SecurityIdentity securityIdentity;
    private final MonitoringService monitoringService;
    private final EnvironmentMapper environmentMapper;
    private final ClusterMapper clusterMapper;

    @Inject
    public ClusterResourcesRest(CollyStorage collyStorage,
                               SecurityIdentity securityIdentity,
                               MonitoringService monitoringService,
                               EnvironmentMapper environmentMapper,
                               ClusterMapper clusterMapper) {
        this.collyStorage = collyStorage;
        this.securityIdentity = securityIdentity;
        this.monitoringService = monitoringService;
        this.environmentMapper = environmentMapper;
        this.clusterMapper = clusterMapper;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/clusters")
    public List<ClusterDTO> getClusters() {
        List<Cluster> clusters = collyStorage.getClusters();
        return clusterMapper.toDTOs(clusters);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/environments")
    public List<EnvironmentDTO> getEnvironments() {
        List<Environment> environments = collyStorage.getEnvironments();
        return environmentMapper.toDTOs(environments);
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

    @DELETE
    @Path("/environments/{envId}")
    @RolesAllowed("admin")
    public void deleteEnvironment(@PathParam("envId") String id) {
        collyStorage.deleteEnvironment(id);
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

