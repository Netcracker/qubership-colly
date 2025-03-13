package org.qubership.colly.storage;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.qubership.colly.db.Cluster;
import org.qubership.colly.db.Environment;
import org.qubership.colly.db.Namespace;

import java.util.List;

@ApplicationScoped
public class NamespaceRepository implements PanacheRepository<Namespace> {
    public Namespace findByName(String name){
        return find("name", name).firstResult();
    }

    public Namespace findByNameAndCluster(String name, String clusterName){
        return find("name = ?1 and cluster.name = ?2", name, clusterName).firstResult();

    }

    public Namespace findByUid(String uid){
        return find("uid", uid).firstResult();
    }

    public List<Namespace> findNamespacesByCluster(Cluster cluster) {
        return find("cluster", cluster).list();
    }
}
