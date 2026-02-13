package org.qubership.colly.achka;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jetbrains.annotations.NotNull;
import org.qubership.colly.db.data.DeploymentItem;
import org.qubership.colly.db.data.DeploymentItemType;
import org.qubership.colly.db.data.DeploymentOperation;
import org.qubership.colly.db.data.DeploymentStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.compare.ComparableUtils.max;

@ApplicationScoped
public class AchKubernetesAgentService {

    private final AchKubernetesAgentClientFactory clientFactory;
    private final Pattern productPattern;
    private final Pattern projectPattern;


    @Inject
    public AchKubernetesAgentService(AchKubernetesAgentClientFactory clientFactory,
                                     @ConfigProperty(name = "colly.environment-operational-service.sd.product.pattern") String sdProductPattern,
                                     @ConfigProperty(name = "colly.environment-operational-service.sd.project.pattern") String sdProjectPattern) {
        this.clientFactory = clientFactory;
        this.productPattern = Pattern.compile(sdProductPattern);
        this.projectPattern = Pattern.compile(sdProjectPattern);
    }

    public List<DeploymentOperation> getDeploymentOperations(String achkaUrl, List<String> namespaceNames) {
        AchKubernetesAgentClient achkaClient = clientFactory.create(achkaUrl);
        List<DeploymentOperation> deploymentOperations = new ArrayList<>();
        AchKubernetesAgentClient.AchkaResponse achkaResponse = achkaClient.versions(namespaceNames, "deployment_session_id");
        for (Map.Entry<String, List<ApplicationsVersion>> entry : achkaResponse.deploymentSessionIdToApplicationVersions().entrySet()) {
            if (entry.getKey().equals("None")) {
                Log.error("Invalid deployment session id: " + entry);
                continue;
            }
            List<ApplicationsVersion> applicationsVersions = entry.getValue();

            Instant completedAt = Instant.MIN;
            List<DeploymentItem> deploymentItems = new ArrayList<>();
            Map<String, List<ApplicationsVersion>> sdToApplications = applicationsVersions.stream().collect(Collectors.groupingBy(ApplicationsVersion::source));
            for (Map.Entry<String, List<ApplicationsVersion>> sdNameToAppVers : sdToApplications.entrySet()) {
                List<ApplicationsVersion> sdApplicationsVersions = sdNameToAppVers.getValue();
                if (sdApplicationsVersions.isEmpty()) {
                    Log.warn("No applications versions found for SD: " + sdNameToAppVers.getKey());
                    continue;
                }

                ApplicationsVersion latest = sdApplicationsVersions.stream()
                        .max(Comparator.comparing(appVer -> Instant.ofEpochMilli(Long.parseLong(appVer.deployDate()))))
                        .orElseThrow();
                completedAt = max(completedAt, Instant.ofEpochMilli(Long.parseLong(latest.deployDate())));
                long failedApps = sdApplicationsVersions.stream().filter(appVer -> appVer.deployStatus().equals("FAILED")).count();
                DeploymentStatus status = failedApps > 0 ? DeploymentStatus.FAILED : DeploymentStatus.SUCCESS;
                DeploymentItemType type = calculateSdType(sdNameToAppVers);
                deploymentItems.add(new DeploymentItem(sdNameToAppVers.getKey(), status, type));
            }
            deploymentOperations.add(new DeploymentOperation(completedAt, deploymentItems));
        }
        return deploymentOperations;
    }

    private @NotNull DeploymentItemType calculateSdType(Map.Entry<String, List<ApplicationsVersion>> sdNameToAppVers) {
        DeploymentItemType type;

        if (productPattern.matcher(sdNameToAppVers.getKey()).find()) {
            type = DeploymentItemType.PRODUCT;
        } else if (projectPattern.matcher(sdNameToAppVers.getKey()).find()) {
            type = DeploymentItemType.PROJECT;
        } else {
            Log.warn("SD name " + sdNameToAppVers.getKey() + " does not match neither product nor project pattern, defaulting to UNKNOWN");
            type = DeploymentItemType.UNKNOWN;
        }
        return type;
    }
}
