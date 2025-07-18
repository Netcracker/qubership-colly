package org.qubership.colly.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.qubership.colly.db.data.EnvironmentStatus;
import org.qubership.colly.db.data.EnvironmentType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record EnvironmentDTO(
        Long id,
        String name,
        List<NamespaceDTO> namespaces,
        ClusterDTO cluster,
        String owner,
        String team,
        EnvironmentStatus status,
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate expirationDate,
        EnvironmentType type,
        List<String> labels,
        String description,
        String deploymentVersion,
        Instant cleanInstallationDate,
        Map<String, String> monitoringData
) {

}
