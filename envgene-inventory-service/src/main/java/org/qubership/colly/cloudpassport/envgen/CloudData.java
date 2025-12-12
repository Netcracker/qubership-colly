package org.qubership.colly.cloudpassport.envgen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CloudData(
        @JsonProperty("CLOUD_API_HOST")
        String cloudApiHost,
        @JsonProperty("CLOUD_API_PORT")
        String cloudApiPort,
        @JsonProperty("CLOUD_DEPLOY_TOKEN")
        String cloudDeployToken,
        @JsonProperty("CLOUD_PROTOCOL")
        String cloudProtocol,
        @JsonProperty("CLOUD_DASHBOARD_URL")
        String cloudDashboardUrl,
        @JsonProperty("CMDB_URL")
        String cloudCmdbUrl) {
}
