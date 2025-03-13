package org.qubership.colly.storage;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.qubership.colly.db.ConfigMap;

@ApplicationScoped
public class ConfigMapRepository implements PanacheRepository<ConfigMap> {
    public ConfigMap findByUid(String uid) {
        return find("uid", uid).firstResult();
    }
}
