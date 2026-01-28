package org.qubership.colly.cloudpassport;

import java.util.Set;

public record CloudPassport(String name, String token, String cloudApiHost, String cloudPublicHost,
                            Set<CloudPassportEnvironment> environments, String monitoringUrl, GitInfo gitInfo,
                            String dashboardUrl, String dbaasUrl, String deployerUrl, String argoUrl) {
}
