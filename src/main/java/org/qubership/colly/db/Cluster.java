package org.qubership.colly.db;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import java.util.List;

@Entity(name = "clusters")
public class Cluster extends PanacheEntityBase {
    @Id
    public String name;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Environment> environments;


    public Cluster(String name, List<Environment> environments) {
        this.name = name;
        this.environments = environments;
    }

    public Cluster() {
    }
}
