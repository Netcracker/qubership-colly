package org.qubership.colly.cloudpassport.envgen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DevopsData(
        @JsonProperty("ARGOCD_URL")
        String argocdUrl,
        @JsonProperty("ACHKA_URL")
        String achkaUrl) {
}
