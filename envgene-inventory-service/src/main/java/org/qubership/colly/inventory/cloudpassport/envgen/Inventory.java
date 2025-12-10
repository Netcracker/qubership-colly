package org.qubership.colly.inventory.cloudpassport.envgen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Inventory {
    private String environmentName;
    private String description;
    private String owners;
    private InventoryMetadata metadata;

    public String getEnvironmentName() {
        return environmentName;
    }

    public void setEnvironmentName(String environmentName) {
        this.environmentName = environmentName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOwners() {
        return owners;
    }

    public void setOwners(String owners) {
        this.owners = owners;
    }

    public InventoryMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(InventoryMetadata metadata) {
        this.metadata = metadata;
    }
}
