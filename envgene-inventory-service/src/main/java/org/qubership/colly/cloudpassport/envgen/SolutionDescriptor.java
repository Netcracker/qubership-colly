package org.qubership.colly.cloudpassport.envgen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.qubership.colly.cloudpassport.SdApplication;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SolutionDescriptor(String version, String type, String deployMode, List<SdApplication> applications) {
}
