package org.qubership.colly.cloudpassport.envgen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EnvTemplate(Map<String, List<String>> envSpecificParamsets,
                          Map<String, List<String>> envSpecificTechnicalParamsets,
                          Map<String, List<String>> envSpecificE2EParamsets) {
}
