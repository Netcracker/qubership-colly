package org.qubership.colly;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.dto.*;
import org.qubership.colly.projectrepo.Project;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/colly/v2/inventory-service")
@SecurityRequirement(name = "SecurityScheme")
public class InventoryServiceRest {

    private final CollyStorage collyStorage;
    private final SecurityIdentity securityIdentity;
    private final DtoMapper dtoMapper;

    @Inject
    public InventoryServiceRest(CollyStorage collyStorage,
                                SecurityIdentity securityIdentity,
                                DtoMapper dtoMapper) {
        this.collyStorage = collyStorage;
        this.securityIdentity = securityIdentity;
        this.dtoMapper = dtoMapper;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/projects")
    public List<ProjectDto> getProjects() {
        return dtoMapper.toProjectDtos(collyStorage.getProjects());
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/projects/{id}")
    public ProjectDto getProject(@PathParam("id") String id) {
        Project project = collyStorage.getProject(id);
        if (project == null) {
            throw new NotFoundException("Project with id =" + id + " is not found");
        }
        return dtoMapper.toProjectDto(project);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/internal/cluster-infos")
    public List<InternalClusterInfoDto> getInternalClusterInfo() {
        return dtoMapper.toClusterInfoDtos(collyStorage.getClusters(null));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/clusters")
    public List<ClusterDto> getClusters(@QueryParam("projectId") String projectId) {
        return dtoMapper.toClusterDtos(collyStorage.getClusters(projectId));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/clusters/{clusterId}")
    public ClusterDto getCluster(@PathParam("clusterId") String id) {
        Cluster cluster = collyStorage.getCluster(id);
        if (cluster == null) {
            throw new NotFoundException("Cluster with id =" + id + " is not found");
        }
        return dtoMapper.toClusterDto(cluster);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/environments")
    public List<EnvironmentDto> getEnvironments(@QueryParam("projectId") String projectId) {
        return dtoMapper.toDtos(collyStorage.getEnvironments(projectId));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/environments/{environmentId}")
    public EnvironmentDto getEnvironmentById(@PathParam("environmentId") String id) {
        Environment environment = collyStorage.getEnvironment(id);
        if (environment == null) {
            throw new NotFoundException("Environment with id =" + id + " is not found");
        }
        return dtoMapper.toDto(environment);
    }

    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/environments/{environmentId}")
    @RolesAllowed("admin")
    public Response patchEnvironment(@PathParam("environmentId") String id,
                                     PatchEnvironmentDto updateDto) {
        try {
            EnvironmentDto updatedEnvironment = dtoMapper.toDto(collyStorage.updateEnvironment(id, updateDto));
            return Response.ok(updatedEnvironment).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Failed to update environment: " + e.getMessage()))
                    .build();
        }
    }


    @POST
    @Path("/manual-sync")
    @Produces(MediaType.APPLICATION_JSON)
    public void syncEnvironmentsWithGit() {
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

}

