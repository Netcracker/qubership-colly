package org.qubership.colly;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.dto.ApplicationMetadata;
import org.qubership.colly.dto.ClusterDTO;
import org.qubership.colly.dto.EnvironmentDTO;
import org.qubership.colly.mapper.ClusterMapper;
import org.qubership.colly.mapper.EnvironmentMapper;
import org.qubership.colly.monitoring.MonitoringService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/colly/v2/operational-service")
@SecurityRequirement(name = "SecurityScheme")
public class ClusterResourcesRest {

    private final CollyStorage collyStorage;
    private final SecurityIdentity securityIdentity;
    private final MonitoringService monitoringService;
    private final ClusterMapper clusterMapper;
    private final EnvironmentMapper environmentMapper;

    @Inject
    public ClusterResourcesRest(CollyStorage collyStorage,
                                SecurityIdentity securityIdentity,
                                MonitoringService monitoringService,
                                ClusterMapper clusterMapper, EnvironmentMapper environmentMapper) {
        this.collyStorage = collyStorage;
        this.securityIdentity = securityIdentity;
        this.monitoringService = monitoringService;
        this.clusterMapper = clusterMapper;
        this.environmentMapper = environmentMapper;
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
    @Path("/clusters/{clusterId}")
    public ClusterDTO getClusterById(@PathParam("clusterId") String clusterId) {
        Cluster cluster = collyStorage.getCluster(clusterId);
        if (cluster == null) {
            throw new NotFoundException("Cluster with id =" + clusterId + " is not found");
        }
        return clusterMapper.toDTO(cluster);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/environments")
    public List<EnvironmentDTO> getEnvironments() {
        return collyStorage.getEnvironments();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/environments/{environmentId}")
    public EnvironmentDTO getEnvironmentById(@PathParam("environmentId") String environmentId) {
        Environment environment = collyStorage.getEnvironment(environmentId);
        if (environment == null) {
            throw new NotFoundException("Environment with id =" + environmentId + " is not found");
        }

        return environmentMapper.toDTO(environment);
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

