package org.qubership.colly.db;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.util.Collections;
import java.util.List;

@Entity(name = "environments")
@JsonInclude(JsonInclude.Include.ALWAYS)
public class Environment extends PanacheEntity {

    public String name;
    public String owner;
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public EnvironmentStatus status = EnvironmentStatus.FREE;

    @ManyToOne
    @JoinColumn(referencedColumnName = "name")
    public Cluster cluster;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Namespace> namespaces;


    public Environment(String name) {
        this.name = name;
        this.namespaces = new java.util.ArrayList<>();
    }

    public Environment() {
    }

    public List<Namespace> getNamespaces() {
        return Collections.unmodifiableList(namespaces);
    }

    public void setNamespaces(List<Namespace> namespaces) {
        this.namespaces = namespaces;
    }

    public void addNamespace(Namespace namespace) {
        this.namespaces.add(namespace);
    }

}

