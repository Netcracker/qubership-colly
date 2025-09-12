package org.qubership.colly.db.data;

public class Namespace {
    private String uid;
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

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
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
