package org.qubership.colly.cloudpassport.envgen;

import java.util.List;
import java.util.Map;

public record EnvTemplate(Map<String, List<String>> envSpecificParamsets,
                          Map<String, List<String>> envSpecificTechnicalParamsets,
                          Map<String, List<String>> envSpecificE2EParamsets) {
}
