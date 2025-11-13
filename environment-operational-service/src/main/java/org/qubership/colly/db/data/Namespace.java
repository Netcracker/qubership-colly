package org.qubership.colly.db.data;

public class Namespace {
    private String id;
    private String name;
    private String clusterId;
    private String environmentId;
    private Boolean existsInK8s;

    public boolean getExistsInK8s() {
        return existsInK8s != null && existsInK8s;
    }

    public void setExistsInK8s(boolean existsInK8s) {
        this.existsInK8s = existsInK8s;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public String getEnvironmentId() {
        return environmentId;
    }

    public void setEnvironmentId(String environmentId) {
        this.environmentId = environmentId;
    }
}
