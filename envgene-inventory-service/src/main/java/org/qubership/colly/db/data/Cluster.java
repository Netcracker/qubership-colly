package org.qubership.colly.db.data;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.qubership.colly.cloudpassport.GitInfo;

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
    private String description;
    private GitInfo gitInfo;
    private String dashboardUrl;
    private String dbaasUrl;
    private String deployerUrl;
    private String argoUrl;
    private String achkaUrl;
}
