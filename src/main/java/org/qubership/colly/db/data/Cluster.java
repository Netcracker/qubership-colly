package org.qubership.colly.db.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity(name = "clusters")
public class Cluster extends PanacheEntityBase {
    @Id
    public String name;

    @OneToMany(mappedBy = "cluster", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    public List<Environment> environments;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Namespace> namespaces;
    public String description;


    public Cluster(String name) {
        this.name = name;
        this.namespaces = new ArrayList<>();
        this.environments = new ArrayList<>();
    }

    public Cluster() {
    }
}
