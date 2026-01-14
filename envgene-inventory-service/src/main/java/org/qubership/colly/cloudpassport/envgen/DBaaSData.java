package org.qubership.colly.cloudpassport.envgen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DBaaSData(
        @JsonProperty("API_DBAAS_ADDRESS")
        String apiDBaaSAddress) {
}
