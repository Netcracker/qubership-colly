package org.qubership.colly.db;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import java.util.List;

@Entity(name = "namespaces")
public class Namespace extends PanacheEntityBase {
    @Id
    public String uid;

    public String name;
    public String envName;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Deployment> deployments;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    public List<ConfigMap> configMaps;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Pod> pods;

    public Namespace(String uid, String name, String envName, List<Deployment> deployments, List<ConfigMap> configMaps, List<Pod> pods) {
        this.uid = uid;
        this.name = name;
        this.envName = envName;
        this.deployments = deployments;
        this.configMaps = configMaps;
        this.pods = pods;
    }

    public Namespace() {
    }
}
