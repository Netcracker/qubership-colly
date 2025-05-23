package org.qubership.colly.cloudpassport.envgen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CSEData {
    @JsonProperty("MONITORING_NAMESPACE")
    private String monitoringNamespace;

    @JsonProperty("MONITORING_TYPE")
    private String monitoringType;

    public CSEData() {
    }

    public String getMonitoringNamespace() {
        return monitoringNamespace;
    }

    public void setMonitoringNamespace(String monitoringNamespace) {
        this.monitoringNamespace = monitoringNamespace;
    }

    public String getMonitoringType() {
        return monitoringType;
    }

    public void setMonitoringType(String monitoringType) {
        this.monitoringType = monitoringType;
    }
}
