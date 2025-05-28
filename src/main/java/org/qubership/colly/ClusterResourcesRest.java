package org.qubership.colly;

import io.quarkus.logging.Log;
import io.quarkus.oidc.IdToken;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.qubership.colly.db.Cluster;
import org.qubership.colly.db.Environment;

import javax.annotation.security.RolesAllowed;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/colly")
@Authenticated
public class ClusterResourcesRest {
    @Inject
    CollyStorage collyStorage;


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
                                @FormParam("type") String type) {
        if (!securityIdentity.hasRole("admin")) {
            Log.debug("Current user is: " + securityIdentity.getPrincipal().getName());
            throw new NotAuthorizedException("User "+ securityIdentity.getPrincipal().getName() +" is not authorized to change Environment");
        };
        collyStorage.saveEnvironment(id, name, owner, description, status, labels, type);
    }


    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    @IdToken
    JsonWebToken idToken;


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/auth-status")
    public Response getAuthStatus() {
        try {
            if (securityIdentity.isAnonymous()) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(Map.of("authenticated", false))
                        .build();
            }

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("authenticated", true);
            userInfo.put("username", securityIdentity.getPrincipal().getName());
            userInfo.put("roles", securityIdentity.getRoles());
            userInfo.put("isAdmin", securityIdentity.hasRole("admin"));

            if (idToken != null) {
                userInfo.put("email", idToken.getClaim("email"));
                userInfo.put("name", idToken.getClaim("name"));
            }

            return Response.ok(userInfo).build();
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("authenticated", false))
                    .build();
        }
    }


    @POST
    @Path("/logout")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> logout() {
        Map<String, String> response = new HashMap<>();
        response.put("logoutUrl", "/q/oidc/logout");
        return response;
    }

}

