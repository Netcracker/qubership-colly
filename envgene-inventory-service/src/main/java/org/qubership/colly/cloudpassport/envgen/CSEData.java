package org.qubership.colly.cloudpassport.envgen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CSEData(
        @JsonProperty("MONITORING_NAMESPACE")
        String monitoringNamespace,

        @JsonProperty("MONITORING_TYPE")
        String monitoringType,

        @JsonProperty("MONITORING_EXT_MONITORING_QUERY_URL")
        String monitoringExtMonitoringQueryUrl
) {
}
