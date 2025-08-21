package org.qubership.colly.cloudpassport.envgen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CloudData {

    @JsonProperty("CLOUD_API_HOST")
    private String cloudApiHost;
    @JsonProperty("CLOUD_API_PORT")
    private String cloudApiPort;
    @JsonProperty("CLOUD_DEPLOY_TOKEN")
    private String cloudDeployToken;
    @JsonProperty("CLOUD_PROTOCOL")
    private String cloudProtocol;


    public String getCloudApiHost() {
        return cloudApiHost;
    }

    public void setCloudApiHost(String cloudApiHost) {
        this.cloudApiHost = cloudApiHost;
    }

    public String getCloudApiPort() {
        return cloudApiPort;
    }

    public void setCloudApiPort(String cloudApiPort) {
        this.cloudApiPort = cloudApiPort;
    }

    public String getCloudDeployToken() {
        return cloudDeployToken;
    }

    public void setCloudDeployToken(String cloudDeployToken) {
        this.cloudDeployToken = cloudDeployToken;
    }

    public String getCloudProtocol() {
        return cloudProtocol;
    }

    public void setCloudProtocol(String cloudProtocol) {
        this.cloudProtocol = cloudProtocol;
    }
}
