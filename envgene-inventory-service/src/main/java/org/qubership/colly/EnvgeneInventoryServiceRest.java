package org.qubership.colly;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/colly/envgene-inventory-service")
public class EnvgeneInventoryServiceRest {

    private final CollyStorage collyStorage;
    private final SecurityIdentity securityIdentity;

    @Inject
    public EnvgeneInventoryServiceRest(CollyStorage collyStorage,
                                       SecurityIdentity securityIdentity) {
        this.collyStorage = collyStorage;
        this.securityIdentity = securityIdentity;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/clusters")
    public List<Cluster> getClusters() {
        return collyStorage.getClusters();
    }


    @POST
    @Path("/tick")
    @Produces(MediaType.APPLICATION_JSON)
    public void loadEnvironmentsManually() {
        collyStorage.executeTask();
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

}

