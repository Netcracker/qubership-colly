package org.qubership.colly.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.time.LocalDate;
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
