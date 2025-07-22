package org.qubership.colly.db.data;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity(name = "clusters")
public class Cluster extends PanacheEntityBase {
    @Id
    private String name;
    private boolean synced;

    @OneToMany(mappedBy = "cluster", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public List<Environment> environments;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Namespace> namespaces;
    private String description;

    public Cluster(String name) {
        this.name = name;
        this.synced = false;
        this.namespaces = new ArrayList<>();
        this.environments = new ArrayList<>();
    }

    public Cluster() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }
}
