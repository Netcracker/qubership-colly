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
        List<String> owners,
        List<String> teams,
        String status,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate expirationDate,
        String type,
        String role,
        List<String> labels,
        String description,
        String deploymentVersion,
        Instant cleanInstallationDate,
        Map<String, String> monitoringData) {

}
