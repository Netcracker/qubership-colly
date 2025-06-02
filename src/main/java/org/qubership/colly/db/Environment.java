package org.qubership.colly.db;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Entity(name = "environments")
@JsonInclude(JsonInclude.Include.ALWAYS)
public class Environment extends PanacheEntity {

    public String name;
    public String owner;
    public String team;
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public EnvironmentStatus status = EnvironmentStatus.FREE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public EnvironmentType type = EnvironmentType.ENVIRONMENT;

    @ManyToOne
    @JoinColumn(referencedColumnName = "name")
    public Cluster cluster;

    @ElementCollection
    @CollectionTable(name = "monitoring_data_content", joinColumns = @JoinColumn(name = "id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value", columnDefinition = "TEXT")
    public Map<String, String> monitoringData;

    public String deploymentVersions;

    @ElementCollection
    @CollectionTable(name = "environments_labels", joinColumns = @JoinColumn(name = "environment_id"))
    @Column(name = "label")
    private List<String> labels;

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

    public List<String> getLabels() {
        return Collections.unmodifiableList(labels);
    }

    public void setLabels(List<String> labels) {
        this.labels = new ArrayList<>(labels);
    }



    public void addLabel(String label) {
        if (this.labels == null) {
            this.labels = new java.util.ArrayList<>();
        }
        this.labels.add(label);
    }

    public void removeLabel(String label) {
        if (this.labels != null) {
            this.labels.remove(label);
        }
    }
}

