package org.qubership.colly.db;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity(name = "environments")
public class Environment extends PanacheEntity {

    public String name;
    public String owner;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Namespace> namespaces;

    public Environment(String name, List<Namespace> namespaces) {
        this.name = name;
        this.namespaces = new ArrayList<>(namespaces);
    }

    public Environment() {
    }

    public void addNamespace(Namespace namespace) {
        this.namespaces.add(namespace);
    }

    public List<Namespace> getNamespaces() {
        return Collections.unmodifiableList(namespaces);
    }

    public void setNamespaces(List<Namespace> namespaces) {
        this.namespaces = new ArrayList<>(namespaces);
    }
}

