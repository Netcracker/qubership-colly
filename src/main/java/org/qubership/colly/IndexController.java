package org.qubership.colly;


import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import org.qubership.colly.db.Environment;

import java.util.List;

@Path("index.html")
@Produces(MediaType.TEXT_HTML)
public class IndexController {

    @Inject
    ClusterResourcesRest clusterResourcesRest;

    @Inject
    Template index;

    @GET
    @Blocking
    public TemplateInstance index() {
        List<Environment> environments = clusterResourcesRest.getEnvironments();
        return index.data("environments", environments);
    }

}