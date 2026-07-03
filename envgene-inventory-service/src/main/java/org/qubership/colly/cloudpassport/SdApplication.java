package org.qubership.colly.cloudpassport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SdApplication(String version, String deployPostfix) {
}
