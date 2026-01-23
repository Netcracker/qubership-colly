package org.qubership.colly;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.ExampleObject;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
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
    private final String syncCronSchedule;

    @Inject
    public ClusterResourcesRest(CollyStorage collyStorage,
                                SecurityIdentity securityIdentity,
                                MonitoringService monitoringService,
                                ClusterMapper clusterMapper, EnvironmentMapper environmentMapper,
                                @ConfigProperty(name = "colly.environment-operational-service.cron.schedule") String syncCronSchedule) {
        this.collyStorage = collyStorage;
        this.securityIdentity = securityIdentity;
        this.monitoringService = monitoringService;
        this.clusterMapper = clusterMapper;
        this.environmentMapper = environmentMapper;
        this.syncCronSchedule = syncCronSchedule;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/clusters")
    @Operation(
            summary = "Get all clusters with operational data",
            description = "Retrieves a list of all Kubernetes clusters with their synchronization status. Requires authentication."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Successfully retrieved list of clusters",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ClusterDTO.class)
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
    public List<ClusterDTO> getClusters() {
        List<Cluster> clusters = collyStorage.getClusters();
        return clusterMapper.toDTOs(clusters);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/clusters/{clusterId}")
    @Operation(
            summary = "Get cluster by ID with operational data",
            description = "Retrieves detailed operational information about a specific cluster by its ID. Requires authentication."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Successfully retrieved cluster details",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ClusterDTO.class)
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
                    responseCode = "404",
                    description = "Cluster not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            examples = @ExampleObject(
                                    value = "{\"error\": \"Cluster with id= xyz is not found\"}"
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
    public ClusterDTO getClusterById(
            @Parameter(
                    description = "ID of the cluster to retrieve",
                    required = true,
                    example = "995f5292-5725-42b6-ad28-0e8629e0f791"
            )
            @PathParam("clusterId") String clusterId) {
        Cluster cluster = collyStorage.getCluster(clusterId);
        if (cluster == null) {
            throw new NotFoundException("Cluster with id =" + clusterId + " is not found");
        }
        return clusterMapper.toDTO(cluster);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/environments")
    @Operation(
            summary = "Get all environments with operational data",
            description = "Retrieves a list of all environments with monitoring data, deployment information, and namespace status. Requires authentication."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Successfully retrieved list of environments",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = EnvironmentDTO.class)
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
    public List<EnvironmentDTO> getEnvironments() {
        return collyStorage.getEnvironments();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/environments/{environmentId}")
    @Operation(
            summary = "Get environment by ID with operational data",
            description = "Retrieves detailed operational information about a specific environment by its ID, including monitoring metrics and deployment status. Requires authentication."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Successfully retrieved environment details",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = EnvironmentDTO.class)
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
                    responseCode = "404",
                    description = "Environment not found",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            examples = @ExampleObject(
                                    value = "{\"error\": \"Environment with id= xyz is not found\"}"
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
    public EnvironmentDTO getEnvironmentById(
            @Parameter(
                    description = "ID of the environment to retrieve",
                    required = true,
                    example = "96180fe7-f025-465f-bbbf-5e83f301a614"
            )
            @PathParam("environmentId") String environmentId) {
        Environment environment = collyStorage.getEnvironment(environmentId);
        if (environment == null) {
            throw new NotFoundException("Environment with id =" + environmentId + " is not found");
        }

        return environmentMapper.toDTO(environment);
    }

    @POST
    @Path("/manual-sync")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Manually trigger Kubernetes synchronization",
            description = "Triggers a manual synchronization of cluster and environment data from Kubernetes API. This will fetch the latest state from Kubernetes and update the operational data. Requires authentication."
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
            responseCode = "404",
            description = "Cluster not found",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            value = "{\"error\": \"Cluster with id= xyz is not found\"}"
                    )
            )
    )
    @APIResponse(
            responseCode = "500",
            description = "Internal server error - synchronization failed",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    examples = @ExampleObject(
                            value = "{\"error\": \"Failed to synchronize with Kubernetes: Connection timeout\"}"
                    )
            )
    )
    public void syncClustersAndEnvironmentsWithK8s(
            @Parameter(
                    description = "Optional cluster ID to sync only a specific cluster. If not provided, all clusters will be synchronized.",
                    example = "995f5292-5725-42b6-ad28-0e8629e0f791"
            )
            @QueryParam("clusterId") String clusterId) {
        if (clusterId == null || clusterId.isEmpty()) {
            collyStorage.syncAllClusters();
        } else {
            collyStorage.syncCluster(clusterId);
        }

    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/auth-status")
    @PermitAll
    @Operation(
            summary = "Get authentication status",
            description = "Returns the current user's authentication status, including username and admin role information. This endpoint is accessible without authentication."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Successfully retrieved authentication status",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            examples = @ExampleObject(
                                    name = "authenticated-user",
                                    summary = "Authenticated user status",
                                    value = """
                                            {
                                              "authenticated": true,
                                              "username": "john.doe",
                                              "isAdmin": false
                                            }
                                            """
                            )
                    )
            ),
            @APIResponse(
                    responseCode = "401",
                    description = "Unauthorized - user is not authenticated",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            examples = @ExampleObject(
                                    name = "unauthenticated-user",
                                    summary = "Unauthenticated user status",
                                    value = """
                                            {
                                              "authenticated": false
                                            }
                                            """
                            )
                    )
            )
    })
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
    @Operation(
            summary = "Get application metadata",
            description = "Retrieves application metadata including available monitoring parameters that can be displayed. Requires authentication."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Successfully retrieved application metadata",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = ApplicationMetadata.class),
                            examples = @ExampleObject(
                                    name = "metadata-response",
                                    summary = "Example metadata response",
                                    value = """
                                            {
                                              "monitoringColumns": [
                                                "cpu_usage",
                                                "memory_usage",
                                                "pod_count"
                                              ]
                                            }
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
    public ApplicationMetadata getMetadata() {
        List<String> parameters = monitoringService.getParameters();
        return new ApplicationMetadata(parameters, syncCronSchedule);
    }
}

