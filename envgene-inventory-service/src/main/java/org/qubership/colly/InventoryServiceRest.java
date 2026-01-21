package org.qubership.colly;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
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
    @Operation(
            summary = "Get all clusters",
            description = "Retrieves a list of all Kubernetes clusters available in the inventory. Requires authentication."
    )

    @APIResponse(
            responseCode = "200",
            description = "Successfully retrieved list of clusters",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ClusterDto.class),
                    examples = @ExampleObject(
                            name = "clusters-list",
                            summary = "Example list of clusters",
                            value = """
                                    [
                                      {
                                        "id": "bd75a053-1210-4b9a-9fe1-9af265b006c9",
                                        "name": "prod-cluster-01"
                                      },
                                      {
                                        "id": "995f5292-5725-42b6-ad28-0e8629e0f791",
                                        "name": "dev-cluster-01"
                                      },
                                      {
                                        "id": "a1b2c3d4-5678-90ab-cdef-1234567890ab",
                                        "name": "staging-cluster-01"
                                      }
                                    ]
                                    """
                    )
            )
    )
    @APIResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            value = "{\"error\": \"Authentication required\"}"
                    )
            )
    )
    @APIResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            value = "{\"error\": \"Internal server error occurred\"}"
                    )
            )
    )
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
    @Operation(
            summary = "Get all environments",
            description = "Retrieves a list of all available environments with their details including namespaces, clusters, owners, teams, and metadata. Requires authentication."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Successfully retrieved list of environments",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = EnvironmentDto.class),
                            examples = @ExampleObject(
                                    name = "environments-list",
                                    summary = "Example list of environments",
                                    value = """
                                            [
                                              {
                                                "id": "96180fe7-f025-465f-bbbf-5e83f301a614",
                                                "name": "prod-env-1",
                                                "description": "Production environment for main application",
                                                "namespaces": [
                                                  {
                                                    "id": "34f89c4d-bcc3-4eff-b271-6fdcdaf977c9",
                                                    "name": "prod-env-1-app"
                                                  },
                                                  {
                                                    "id": "6d3eff88-f35b-471c-b84a-923765861feb",
                                                    "name": "prod-env-1-services"
                                                  }
                                                ],
                                                "cluster": {
                                                  "id": "995f5292-5725-42b6-ad28-0e8629e0f791",
                                                  "name": "prod-cluster-01"
                                                },
                                                "owners": ["john.doe", "jane.smith"],
                                                "labels": ["production", "critical"],
                                                "teams": ["DevOps"],
                                                "status": "IN_USE",
                                                "expirationDate": null,
                                                "type": "ENVIRONMENT",
                                                "role": "production",
                                                "region": "us-east-1"
                                              },
                                              {
                                                "id": "b41b5769-239c-4297-9ef8-8cb2866f186e",
                                                "name": "dev-env-test",
                                                "description": "Development environment for testing",
                                                "namespaces": [
                                                  {
                                                    "id": "8c1e53b8-74af-48cb-869d-e814447b0c91",
                                                    "name": "dev-env-test-apps"
                                                  }
                                                ],
                                                "cluster": {
                                                  "id": "bd75a053-1210-4b9a-9fe1-9af265b006c9",
                                                  "name": "dev-cluster-01"
                                                },
                                                "owners": ["dev.team"],
                                                "labels": ["CI"],
                                                "teams": ["Development", "QA"],
                                                "status": "FREE",
                                                "expirationDate": "2025-12-31",
                                                "type": "ENVIRONMENT",
                                                "role": "development",
                                                "region": "eu-west-1"
                                              }
                                            ]
                                            """
                            )
                    )
            ),
            @APIResponse(
                    responseCode = "401",
                    description = "Unauthorized - authentication required",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            examples = @ExampleObject(
                                    value = "{\"error\": \"Authentication required\"}"
                            )
                    )
            ),
            @APIResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            examples = @ExampleObject(
                                    value = "{\"error\": \"Internal server error occurred\"}"
                            )
                    )
            )
    })
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
    @Operation(
            summary = "Partially update an environment",
            description = "Updates specific fields of an existing environment. Only provided fields will be updated. Requires admin role."
    )
    @APIResponse(
            responseCode = "200",
            description = "Environment successfully updated",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = EnvironmentDto.class),
                    examples = @ExampleObject(
                            name = "updated-environment",
                            summary = "Example of updated environment",
                            value = """
                                    {
                                      "id": "96180fe7-f025-465f-bbbf-5e83f301a614",
                                      "name": "prod-env-1",
                                      "description": "Updated production environment description",
                                      "namespaces": [
                                        {
                                          "id": "34f89c4d-bcc3-4eff-b271-6fdcdaf977c9",
                                          "name": "prod-env-1-app"
                                        }
                                      ],
                                      "cluster": {
                                        "id": "995f5292-5725-42b6-ad28-0e8629e0f791",
                                        "name": "prod-cluster-01"
                                      },
                                      "owners": ["john.doe", "jane.smith", "new.owner"],
                                      "labels": ["production", "critical", "updated"],
                                      "teams": ["Platform", "DevOps", "SRE"],
                                      "status": "IN_USE",
                                      "expirationDate": "2025-12-31",
                                      "type": "ENVIRONMENT",
                                      "role": "production",
                                      "region": "us-east-1"
                                    }
                                    """
                    )
            )
    )
    @APIResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            value = "{\"error\": \"Authentication required\"}"
                    )
            )
    )
    @APIResponse(
            responseCode = "403",
            description = "Forbidden - admin role required",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            value = "{\"error\": \"Access denied. Admin role required.\"}"
                    )
            )
    )
    @APIResponse(
            responseCode = "404",
            description = "Environment not found",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            value = "{\"error\": \"Environment with id= 96180fe7-f025-465f-bbbf-5e83f301a614 not found \"}"
                    )
            )
    )
    @APIResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            value = "{\"error\": \"Failed to update environment: Internal error occurred\"}"
                    )
            )
    )
    public Response patchEnvironment(
            @Parameter(
                    description = "ID of the environment to update (UUID format)",
                    required = true,
                    example = "96180fe7-f025-465f-bbbf-5e83f301a614"
            )
            @PathParam("environmentId") String id,

            @RequestBody(
                    description = "Partial update data. Only provided fields will be updated. To clear expirationDate, send empty string.",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = PatchEnvironmentDto.class),
                            examples = {
                                    @ExampleObject(
                                            name = "update-description-and-owners",
                                            summary = "Update description and add owners",
                                            value = """
                                                    {
                                                      "description": "Updated environment description",
                                                      "owners": ["john.doe@company.com", "jane.smith@company.com", "new.owner@company.com"]
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "update-status-and-expiration",
                                            summary = "Change status and set expiration date",
                                            value = """
                                                    {
                                                      "status": "IN_USE",
                                                      "expirationDate": "2025-12-31"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "clear-expiration-date",
                                            summary = "Clear expiration date",
                                            value = """
                                                    {
                                                      "expirationDate": ""
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "update-labels-and-teams",
                                            summary = "Update labels and teams",
                                            value = """
                                                    {
                                                      "labels": ["production", "critical", "high-priority"],
                                                      "teams": ["Platform", "DevOps", "SRE"]
                                                    }
                                                    """
                                    )
                            }
                    )
            )
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
    @Operation(
            summary = "Manually trigger Git synchronization",
            description = "Triggers a manual synchronization of environment data from the Git repository. This will fetch the latest configuration from Git and update the inventory. Requires authentication."
    )
    @APIResponse(
            responseCode = "204",
            description = "Synchronization triggered successfully (no content returned)"
    )
    @APIResponse(
            responseCode = "401",
            description = "Unauthorized - authentication required",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            value = "{\"error\": \"Authentication required\"}"
                    )
            )
    )
    @APIResponse(
            responseCode = "500",
            description = "Internal server error - synchronization failed",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            value = "{\"error\": \"Failed to synchronize with Git: Connection timeout\"}"
                    )
            )
    )
    public void syncEnvironmentsWithGit(@QueryParam("projectId") String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            collyStorage.syncAll();
        } else {
            collyStorage.syncProject(projectId);
        }
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

