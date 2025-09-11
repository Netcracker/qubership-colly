package org.qubership.colly.db.data;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Entity(name = "environments")
public class Environment extends PanacheEntity {

    private String name;
    private String owner;
    private String team;
    @Column(columnDefinition = "TEXT")
    private String description;
    private LocalDate expirationDate;
    private Instant cleanInstallationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnvironmentStatus status = EnvironmentStatus.FREE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnvironmentType type = EnvironmentType.ENVIRONMENT;

    @ManyToOne
    @JoinColumn(referencedColumnName = "name")
    private Cluster cluster;

    @ElementCollection
    @CollectionTable(name = "monitoring_data_content", joinColumns = @JoinColumn(name = "id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value", columnDefinition = "TEXT")
    private Map<String, String> monitoringData;

    @Column(columnDefinition = "TEXT")
    private String deploymentVersion;

    @ElementCollection
    @CollectionTable(name = "environments_labels", joinColumns = @JoinColumn(name = "environment_id"))
    @Column(name = "label")
    private List<String> labels;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Namespace> namespaces;

    @Enumerated(EnumType.STRING)
    private DeploymentStatus deploymentStatus;

    @Column(columnDefinition = "TEXT")
    private String ticketLinks;

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

    public String getDeploymentVersion() {
        return deploymentVersion;
    }

    public void setDeploymentVersion(String deploymentVersion) {
        this.deploymentVersion = deploymentVersion;
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

    public Map<String, String> getMonitoringData() {
        return monitoringData;
    }

    public void setMonitoringData(Map<String, String> monitoringData) {
        this.monitoringData = monitoringData;
    }

    public Instant getCleanInstallationDate() {
        return cleanInstallationDate;
    }

    public void setCleanInstallationDate(Instant cleanInstallationDate) {
        this.cleanInstallationDate = cleanInstallationDate;
    }

    public DeploymentStatus getDeploymentStatus() {
        return deploymentStatus;
    }

    public void setDeploymentStatus(DeploymentStatus deploymentStatus) {
        this.deploymentStatus = deploymentStatus;
    }

    public String getTicketLinks() {
        return ticketLinks;
    }

    public void setTicketLinks(String ticketLinks) {
        this.ticketLinks = ticketLinks;
    }
}
