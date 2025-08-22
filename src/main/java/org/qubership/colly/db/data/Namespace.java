package org.qubership.colly.db.data;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity(name = "namespaces")
public class Namespace extends PanacheEntityBase {
    @Id
    private String uid;

    private String name;
    @ManyToOne()
    @JoinColumn(referencedColumnName = "name")
    private Cluster cluster;

    @ManyToOne()
    @JoinColumn(referencedColumnName = "id")
    private Environment environment;

    private Boolean existsInK8s;

    public boolean getExistsInK8s() {
        return existsInK8s != null && existsInK8s;
    }

    public void setExistsInK8s(boolean existsInK8s) {
        this.existsInK8s = existsInK8s;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Cluster getCluster() {
        return cluster;
    }

    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}
