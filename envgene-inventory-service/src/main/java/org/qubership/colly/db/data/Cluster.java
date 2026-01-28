package org.qubership.colly.db.data;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.qubership.colly.cloudpassport.GitInfo;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
public class Cluster {
    private String id;
    private String name;
    private String token;
    private String cloudApiHost;
    private String cloudPublicHost;
    private String monitoringUrl;
    @Builder.Default
    private List<Environment> environments = new ArrayList<>();
    private String description;
    private GitInfo gitInfo;
    private String dashboardUrl;
    private String dbaasUrl;
    private String deployerUrl;
    private String argoUrl;
    public List<Environment> getEnvironments() {
        return environments != null ? environments : new ArrayList<>();
    }

}
