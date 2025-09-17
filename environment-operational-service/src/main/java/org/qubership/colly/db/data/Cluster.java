package org.qubership.colly.db.data;

import java.util.ArrayList;
import java.util.List;

public class Cluster {
    private String name;
    private Boolean synced;
    private List<String> environmentIds;
    private List<String> namespaceIds;
    private String description;

    public Cluster(String name) {
        this.name = name;
        this.synced = false;
        this.namespaceIds = new ArrayList<>();
        this.environmentIds = new ArrayList<>();
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

    public boolean getSynced() {
        return synced != null && synced;
    }

    public void setSynced(Boolean synced) {
        this.synced = synced;
    }

    public List<String> getEnvironmentIds() {
        return environmentIds;
    }

    public void setEnvironmentIds(List<String> environmentIds) {
        this.environmentIds = environmentIds;
    }

    public List<String> getNamespaceIds() {
        return namespaceIds;
    }

    public void setNamespaceIds(List<String> namespaceIds) {
        this.namespaceIds = namespaceIds;
    }
}
