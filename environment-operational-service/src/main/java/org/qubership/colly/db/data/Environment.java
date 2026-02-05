package org.qubership.colly.db.data;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Setter
@Getter
@NoArgsConstructor
public class Environment {

    private String id;
    private String name;
    private Instant cleanInstallationDate;
    private String clusterId;
    private Map<String, String> monitoringData;
    private String deploymentVersion;
    private List<DeploymentOperation> deploymentOperations;
    private List<String> namespaceIds;

    public Environment(String id, String name) {
        this.setId(id);
        this.setName(name);
        this.namespaceIds = new ArrayList<>();
    }

    public List<String> getNamespaceIds() {
        return namespaceIds != null ? Collections.unmodifiableList(namespaceIds) : Collections.emptyList();
    }

    public void addNamespaceId(String namespaceId) {
        if (this.namespaceIds == null) {
            this.namespaceIds = new ArrayList<>();
        }
        this.namespaceIds.add(namespaceId);
    }

    public List<DeploymentOperation> getDeploymentOperations() {
        return deploymentOperations != null ? Collections.unmodifiableList(deploymentOperations) : Collections.emptyList();
    }

    public void setDeploymentOperations(List<DeploymentOperation> deploymentOperations) {
        this.deploymentOperations = deploymentOperations != null ? new ArrayList<>(deploymentOperations) : null;
    }
}
