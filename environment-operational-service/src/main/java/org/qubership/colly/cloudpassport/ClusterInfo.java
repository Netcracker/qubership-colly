package org.qubership.colly.cloudpassport;

import java.util.Set;

public record ClusterInfo(String id, String name, String token, String cloudApiHost,
                          String cloudPublicHost, Set<CloudPassportEnvironment> environments, String monitoringUrl) {
}
