package org.qubership.colly.db.data;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class Cluster {
    private String id;
    private String name;
    private Boolean synced;
    private Integer numberOfNodes;
    private List<String> environmentIds;
    private List<String> namespaceIds;
}
