package org.qubership.colly.db;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity(name = "deployments")
public class Deployment extends PanacheEntityBase {
    @Id
    public String uid;
    public String name;
    public int replicas;
    @Column(columnDefinition = "TEXT")
    public String configuration;

    public Deployment(String uid, String name, int replicas, String configuration) {
        this.uid = uid;
        this.name = name;
        this.replicas = replicas;
        this.configuration = configuration;
    }

    public Deployment() {
    }
}
