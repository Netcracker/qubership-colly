package org.qubership.colly.achka;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.qubership.colly.db.data.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.compare.ComparableUtils.max;

@ApplicationScoped
public class AchKubernetesAgentService {

    public List<DeploymentOperation> getDeploymentOperations(String cloudPublicHost, List<String> namespaceNames) {
        AchKubernetesAgentClient achkaClient = RestClientBuilder.newBuilder().baseUri(generateAchkaUrl(cloudPublicHost)).build(AchKubernetesAgentClient.class);
        List<DeploymentOperation> deploymentOperations = new ArrayList<>();
        AchKubernetesAgentClient.AchkaResponse achkaResponse = achkaClient.versions(namespaceNames, "deployment_session_id");
        for (String deploymentSessionId : achkaResponse.deploymentSessionIdToApplicationVersions().keySet()) {
            if (deploymentSessionId.equals("None") || !deploymentSessionId.matches(".*:.*")) {
                Log.error("Invalid deployment session id: " + deploymentSessionId);
                continue;
            }
            List<ApplicationsVersion> applicationsVersions = achkaResponse.deploymentSessionIdToApplicationVersions().get(deploymentSessionId);

            Instant completedAt = Instant.MIN;
            List<DeploymentItem> deploymentItems = new ArrayList<>();
            Map<String, List<ApplicationsVersion>> sdToApplications = applicationsVersions.stream().collect(Collectors.groupingBy(ApplicationsVersion::source));
            for (String sdName : sdToApplications.keySet()) {
                List<ApplicationsVersion> sdApplicationsVersions = sdToApplications.get(sdName);
                if (sdApplicationsVersions.isEmpty()) {
                    Log.warn("No applications versions found for SD: " + sdName);
                    continue;
                }

                ApplicationsVersion latest = sdApplicationsVersions.stream()
                        .max(Comparator.comparing(appVer -> Instant.ofEpochMilli(Long.parseLong(appVer.deployDate()))))
                        .orElseThrow();
                completedAt = max(completedAt, Instant.ofEpochMilli(Long.parseLong(latest.deployDate())));
                long failedApps = sdApplicationsVersions.stream().filter(appVer -> appVer.deployStatus().equals("FAILED")).count();
                DeploymentStatus status = failedApps > 0 ? DeploymentStatus.FAILED : DeploymentStatus.SUCCESS;
                deploymentItems.add(new DeploymentItem(sdName, status, DeploymentItemType.PRODUCT, DeploymentMode.ROLLING_UPDATE));
            }
            deploymentOperations.add(new DeploymentOperation(completedAt, deploymentItems));
        }
        return deploymentOperations;
    }

    private String generateAchkaUrl(String cloudPublicHost) {
        return "https://ach-kubernetes-agent-devops-toolkit." + cloudPublicHost;
    }
}
