package org.qubership.colly;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.qubership.colly.dto.ClusterDto;
import org.qubership.colly.dto.InternalClusterInfoDto;
import org.qubership.colly.dto.EnvironmentDto;
import org.qubership.colly.dto.PatchEnvironmentDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/colly/v2/inventory-service")
@Authenticated  // Require authentication for all methods in this class by default
public class EnvgeneInventoryServiceRest {

    private final CollyStorage collyStorage;
    private final SecurityIdentity securityIdentity;
    private final DtoMapper dtoMapper;

    @Inject
    public EnvgeneInventoryServiceRest(CollyStorage collyStorage,
                                       SecurityIdentity securityIdentity,
                                       DtoMapper dtoMapper) {
        this.collyStorage = collyStorage;
        this.securityIdentity = securityIdentity;
        this.dtoMapper = dtoMapper;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/internal/cluster-infos")
    public List<InternalClusterInfoDto> getInternalClusterInfo() {
        return dtoMapper.toClusterInfoDtos(collyStorage.getClusters());
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/clusters")
    public List<ClusterDto> getClusters() {
        return dtoMapper.toClusterDtos(collyStorage.getClusters());
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/environments")
    public List<EnvironmentDto> getEnvironments() {
        return dtoMapper.toDtos(collyStorage.getEnvironments());
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

