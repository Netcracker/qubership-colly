package org.qubership.colly.storage;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.qubership.colly.db.Pod;

@ApplicationScoped
public class PodRepository implements PanacheRepository<Pod> {
    public Pod findByUid(String uid) {
        return find("uid", uid).firstResult();
    }
}
