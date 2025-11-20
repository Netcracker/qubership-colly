package org.qubership.colly;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.dto.ApplicationMetadata;
import org.qubership.colly.dto.ClusterDTO;
import org.qubership.colly.dto.EnvironmentDTO;
import org.qubership.colly.mapper.ClusterMapper;
import org.qubership.colly.monitoring.MonitoringService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/colly/v2/operational-service")
@Authenticated
public class ClusterResourcesRest {

    private final CollyStorage collyStorage;
    private final SecurityIdentity securityIdentity;
    private final MonitoringService monitoringService;
    private final ClusterMapper clusterMapper;

    @Inject
    public ClusterResourcesRest(CollyStorage collyStorage,
                                SecurityIdentity securityIdentity,
                                MonitoringService monitoringService,
                                ClusterMapper clusterMapper) {
        this.collyStorage = collyStorage;
        this.securityIdentity = securityIdentity;
        this.monitoringService = monitoringService;
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
        return collyStorage.getEnvironments();
    }

    @POST
    @Path("/manual-sync")
    @Produces(MediaType.APPLICATION_JSON)
    public void loadEnvironmentsManually() {
        collyStorage.executeTask();
    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/auth-status")
    @PermitAll
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

