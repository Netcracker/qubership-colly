package org.qubership.colly.achka;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import java.net.URI;

@ApplicationScoped
public class AchKubernetesAgentClientFactory {

    public AchKubernetesAgentClient create(String achkaUrl) {
        return RestClientBuilder.newBuilder()
                .baseUri(URI.create(achkaUrl))
                .build(AchKubernetesAgentClient.class);
    }
}
