package org.qubership.colly.db.data;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity(name = "environments")
@JsonInclude(JsonInclude.Include.ALWAYS)
public class Environment extends PanacheEntity {

    private String name;
    private String owner;
    private String team;
    private String description;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expirationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnvironmentStatus status = EnvironmentStatus.FREE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnvironmentType type = EnvironmentType.ENVIRONMENT;

    @ManyToOne
    @JoinColumn(referencedColumnName = "name")
    @JsonIgnore
    private Cluster cluster;


    @ElementCollection
    @CollectionTable(name = "environments_labels", joinColumns = @JoinColumn(name = "environment_id"))
    @Column(name = "label")
    private List<String> labels;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Namespace> namespaces;


    public Environment(String name) {
        this.setName(name);
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

    public List<String> getLabels() {
        return Collections.unmodifiableList(labels);
    }

    public void setLabels(List<String> labels) {
        this.labels = new ArrayList<>(labels);
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate = expirationDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public EnvironmentStatus getStatus() {
        return status;
    }

    public void setStatus(EnvironmentStatus status) {
        this.status = status;
    }

    public EnvironmentType getType() {
        return type;
    }

    public void setType(EnvironmentType type) {
        this.type = type;
    }

    public Cluster getCluster() {
        return cluster;
    }

    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }

}

