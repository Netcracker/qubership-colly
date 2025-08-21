package org.qubership.colly.cloudpassport;

import java.net.URI;
import java.util.Set;

public record CloudPassport(String name, String token, String cloudApiHost,
                            Set<CloudPassportEnvironment> environments, URI monitoringUrl) {
}
