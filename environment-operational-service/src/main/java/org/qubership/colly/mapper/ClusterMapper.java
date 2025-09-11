package org.qubership.colly.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.dto.ClusterDTO;

import java.util.List;

@ApplicationScoped
public class ClusterMapper {

    /**
     * Convert Cluster entity to DTO
     */
    public ClusterDTO toDTO(Cluster entity) {
        if (entity == null) {
            return null;
        }
        return new ClusterDTO(entity.getName(), entity.getDescription(), entity.getSynced());
    }

    /**
     * Convert a list of Cluster entities to DTOs
     */
    public List<ClusterDTO> toDTOs(List<Cluster> entities) {
        return entities.stream()
                .map(this::toDTO)
                .toList();
    }

}
