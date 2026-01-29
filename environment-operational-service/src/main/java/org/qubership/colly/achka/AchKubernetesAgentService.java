package org.qubership.colly.achka;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import java.util.List;

@ApplicationScoped
public class AchKubernetesAgentService {


    public String getInstalledVersion(String cloudPublicHost, String namespace) {

        AchKubernetesAgentClient achkaClient = RestClientBuilder.newBuilder().baseUri(generateAchkaUrl(cloudPublicHost)).build(AchKubernetesAgentClient.class);
        List<ApplicationsVersion> versions = achkaClient.versions(namespace, "source").versions();
        return "todo";
    }

    private String generateAchkaUrl(String cloudPublicHost) {
        return "https://ach-kubernetes-agent-devops-toolkit." + cloudPublicHost;
    }
}
