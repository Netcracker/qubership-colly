package org.qubership.colly.db.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.net.URI;
import java.util.List;

@Entity(name = "clusters")
public class Cluster extends PanacheEntityBase {
    @Id
    private String name;
    @Column(columnDefinition = "TEXT")
    private String token;
    private String cloudApiHost;

    private URI monitoringUrl;

    @OneToMany(mappedBy = "cluster", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    public List<Environment> environments;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    public List<Namespace> namespaces;
    private String description;

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

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getCloudApiHost() {
        return cloudApiHost;
    }

    public void setCloudApiHost(String cloudApiHost) {
        this.cloudApiHost = cloudApiHost;
    }

    public URI getMonitoringUrl() {
        return monitoringUrl;
    }

    public void setMonitoringUrl(URI monitoringUrl) {
        this.monitoringUrl = monitoringUrl;
    }

    public List<Environment> getEnvironments() {
        return environments;
    }

    public void setEnvironments(List<Environment> environments) {
        this.environments = environments;
    }

    public List<Namespace> getNamespaces() {
        return namespaces;
    }

    public void setNamespaces(List<Namespace> namespaces) {
        this.namespaces = namespaces;
    }
}
