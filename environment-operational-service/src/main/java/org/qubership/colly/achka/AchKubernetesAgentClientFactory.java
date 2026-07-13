package org.qubership.colly.achka;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import java.net.URI;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class AchKubernetesAgentClientFactory {

    @ConfigProperty(name = "colly.environment-operational-service.achka-client.connect-timeout-ms", defaultValue = "3000")
    long connectTimeoutMs;

    @ConfigProperty(name = "colly.environment-operational-service.achka-client.read-timeout-ms", defaultValue = "5000")
    long readTimeoutMs;

    public AchKubernetesAgentClient create(String achkaUrl) {
        return RestClientBuilder.newBuilder()
                .baseUri(URI.create(achkaUrl))
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .build(AchKubernetesAgentClient.class);
    }
}
