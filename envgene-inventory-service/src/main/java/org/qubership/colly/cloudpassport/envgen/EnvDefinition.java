package org.qubership.colly.cloudpassport.envgen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EnvDefinition(EnvDefinitionMetadata metadata, Inventory inventory, EnvTemplate envTemplate) {

}
