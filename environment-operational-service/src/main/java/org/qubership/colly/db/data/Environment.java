package org.qubership.colly.db.data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Environment {

    private String id;
    private String name;
    private Instant cleanInstallationDate;
    private EnvironmentType type = EnvironmentType.ENVIRONMENT;//todo discuss and remove
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
