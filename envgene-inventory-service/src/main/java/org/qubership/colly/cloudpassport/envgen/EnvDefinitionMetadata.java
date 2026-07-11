package org.qubership.colly.cloudpassport.envgen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EnvDefinitionMetadata(
        String description,
        List<String> owners,
        List<String> labels,
        List<String> teams,
        String status,
        String expirationDate,
        String type,
        String role,
        String region,
        List<String> accessGroups,
        List<String> effectiveAccessGroups,
        @JsonProperty("ssp_standalone")
        Boolean sspStandalone
) {
    public EnvDefinitionMetadata {
        owners = owners == null ? List.of() : owners;
        labels = labels == null ? List.of() : labels;
        teams = teams == null ? List.of() : teams;
    }
}
