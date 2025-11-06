package org.qubership.colly.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record EnvironmentDTO(
        String id,
        String name,
        List<NamespaceDTO> namespaces,
        ClusterDTO cluster,
        String deploymentVersion,
        Instant cleanInstallationDate,
        Map<String, String> monitoringData) {

}
