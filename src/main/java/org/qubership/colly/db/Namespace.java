package org.qubership.colly.db;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.List;

@Entity(name = "namespaces")
public class Namespace extends PanacheEntityBase {
    @Id
    public String uid;

    public String name;
    @ManyToOne()
    @JoinColumn(referencedColumnName = "name")
    public Cluster cluster;

    @ManyToOne()
    @JoinColumn(referencedColumnName = "id")
    public Environment environment;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Deployment> deployments = new java.util.ArrayList<>();
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    public List<ConfigMap> configMaps = new java.util.ArrayList<>();
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Pod> pods = new java.util.ArrayList<>();


    public void updateDeployments(List<Deployment> deployments) {
        this.deployments.clear();
        this.deployments.addAll(deployments);
    }

    public void updateConfigMaps(List<ConfigMap> configMaps) {
        this.configMaps.clear();
        this.configMaps.addAll(configMaps);
    }

    public void updatePods(List<Pod> pods) {
        this.pods.clear();
        this.pods.addAll(pods);
    }
}
