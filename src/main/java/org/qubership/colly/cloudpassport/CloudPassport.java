package org.qubership.colly.cloudpassport;

import java.net.URI;
import java.util.List;

public record CloudPassport(String name, String token, String cloudApiHost, List<CloudPassportEnvironment> environments, URI monitoringUrl ) {
}
