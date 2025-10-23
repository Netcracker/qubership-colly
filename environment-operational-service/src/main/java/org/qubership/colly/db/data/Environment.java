package org.qubership.colly.db.data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Environment {

    private String id;
    private String name;
    private String team;
    private LocalDate expirationDate;
    private Instant cleanInstallationDate;
    private EnvironmentStatus status = EnvironmentStatus.FREE;
    private EnvironmentType type = EnvironmentType.ENVIRONMENT;
    private String clusterId;
    private Map<String, String> monitoringData;
    private String deploymentVersion;
    private List<String> namespaceIds;
    private DeploymentStatus deploymentStatus;
    private String ticketLinks;

    public Environment(String name) {
        this.setName(name);
        this.namespaceIds = new ArrayList<>();
    }

    public Environment() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }


    public List<String> getNamespaceIds() {
        return namespaceIds != null ? Collections.unmodifiableList(namespaceIds) : Collections.emptyList();
    }

    public void setNamespaceIds(List<String> namespaceIds) {
        this.namespaceIds = namespaceIds;
    }

    public void addNamespaceId(String namespaceId) {
        if (this.namespaceIds == null) {
            this.namespaceIds = new ArrayList<>();
        }
        this.namespaceIds.add(namespaceId);
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

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
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

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
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
