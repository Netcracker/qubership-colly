package org.qubership.colly.db;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.qubership.colly.db.data.Cluster;

@ApplicationScoped
public class ClusterRepository implements PanacheRepository<Cluster> {

    public Cluster findByName(String name){
        return find("name", name).firstResult();
    }
}
