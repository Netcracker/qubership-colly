package org.qubership.colly.cloudpassport.envgen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ParamsetFileData(
        String name,
        Map<String, String> parameters,
        List<ParamsetApplicationData> applications
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParamsetApplicationData(
            String appName,
            Map<String, String> parameters
    ) {
    }
}
