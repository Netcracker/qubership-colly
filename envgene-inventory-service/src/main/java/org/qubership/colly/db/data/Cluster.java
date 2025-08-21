package org.qubership.colly.db.data;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
}
