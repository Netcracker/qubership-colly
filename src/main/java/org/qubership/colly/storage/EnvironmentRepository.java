package org.qubership.colly.storage;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.qubership.colly.db.Environment;

@ApplicationScoped
public class EnvironmentRepository implements PanacheRepository<Environment> {
    public Environment findByNameAndCluster(String environmentName, String clusterName) {
        return find("name = ?1 and cluster.name = ?2", environmentName, clusterName).firstResult();
    }
}
