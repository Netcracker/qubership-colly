package org.qubership.colly.achka;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import java.net.URI;

@ApplicationScoped
public class AchKubernetesAgentClientFactory {

    public AchKubernetesAgentClient create(String cloudPublicHost) {
        String url = "https://ach-kubernetes-agent-devops-toolkit." + cloudPublicHost;
        return RestClientBuilder.newBuilder()
                .baseUri(URI.create(url))
                .build(AchKubernetesAgentClient.class);
    }
}