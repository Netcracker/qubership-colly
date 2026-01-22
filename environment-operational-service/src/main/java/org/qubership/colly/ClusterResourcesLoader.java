package org.qubership.colly;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.credentials.AccessTokenAuthentication;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;
import org.qubership.colly.cloudpassport.CloudPassportNamespace;
import org.qubership.colly.cloudpassport.ClusterInfo;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.data.Namespace;
import org.qubership.colly.db.repository.ClusterRepository;
import org.qubership.colly.db.repository.EnvironmentRepository;
import org.qubership.colly.db.repository.NamespaceRepository;
import org.qubership.colly.monitoring.MonitoringService;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@ApplicationScoped
public class ClusterResourcesLoader {

    private final NamespaceRepository namespaceRepository;
    private final ClusterRepository clusterRepository;
    private final EnvironmentRepository environmentRepository;
    private final MonitoringService monitoringService;

    @ConfigProperty(name = "colly.environment-operational-service.config-map.versions.name")
    String versionsConfigMapName;

    @ConfigProperty(name = "colly.environment-operational-service.config-map.versions.data-field-name")
    String versionsConfigMapDataFieldName;

    @Inject
    public ClusterResourcesLoader(NamespaceRepository namespaceRepository,
                                  ClusterRepository clusterRepository,
                                  EnvironmentRepository environmentRepository,
                                  MonitoringService monitoringService) {
        this.namespaceRepository = namespaceRepository;
        this.clusterRepository = clusterRepository;
        this.environmentRepository = environmentRepository;
        this.monitoringService = monitoringService;
    }


    //@Transactional - removed for Redis
    public void loadClusterResources(ClusterInfo clusterInfo) {
        AccessTokenAuthentication authentication = new AccessTokenAuthentication(clusterInfo.token());
        try {
            ApiClient client = ClientBuilder.standard()
                    .setAuthentication(authentication)
                    .setBasePath(clusterInfo.cloudApiHost())
                    .setVerifyingSsl(false)
                    .build();
            CoreV1Api coreV1Api = new CoreV1Api(client);
            loadClusterResources(coreV1Api, clusterInfo);
        } catch (RuntimeException | IOException e) {
            Log.error("Can't load resources from cluster " + clusterInfo.name(), e);
        }
    }

    //package-private for testing purposes
    void loadClusterResources(CoreV1Api coreV1Api, ClusterInfo clusterInfo) {
        Log.info("Start Loading cluster resources for: " + clusterInfo.name());
        Cluster cluster = clusterRepository.findByName(clusterInfo.name());
        if (cluster == null) {
            cluster = Cluster.builder().id(clusterInfo.id()).name(clusterInfo.name()).build();
            Log.info("Cluster " + clusterInfo.name() + " not found in db. Creating new one.");
            clusterRepository.save(cluster);
        }

        //it is requirzed to set links to cluster only if it was saved to db. so need to invoke persist two
        List<Environment> environments = loadEnvironments(coreV1Api, cluster, clusterInfo.environments(), clusterInfo.monitoringUrl());
        try {
            V1NodeList execute = coreV1Api.listNode().execute();
            int numberOfNodes = execute.getItems().size();
            cluster.setNumberOfNodes(numberOfNodes);
            Log.info("Nodes count for cluster =" + cluster.getName() + " is " + numberOfNodes);
        } catch (ApiException e) {
            Log.error("Can't load nodes from cluster " + cluster.getName() + ". " + e.getMessage());
        }
        cluster.setEnvironmentIds(environments.stream().map(Environment::getId).collect(Collectors.toList()));
        clusterRepository.save(cluster);
        Log.info("Cluster " + clusterInfo.name() + " loaded successfully.");
    }

    private List<Environment> loadEnvironments(CoreV1Api coreV1Api, Cluster cluster, Collection<CloudPassportEnvironment> environments, String monitoringUri) {
        Log.info("Start loading environments for cluster " + cluster.getName());
        CoreV1Api.APIlistNamespaceRequest apilistNamespaceRequest = coreV1Api.listNamespace();
        Map<String, V1Namespace> k8sNamespaces;
        try {
            V1NamespaceList list = apilistNamespaceRequest.execute();
            k8sNamespaces = list.getItems().stream().collect(Collectors.toMap(v1Namespace -> getNameSafely(v1Namespace.getMetadata()), Function.identity()));
            cluster.setSynced(true);
        } catch (ApiException e) {
            k8sNamespaces = new HashMap<>();
            cluster.setSynced(false);
            Log.error("Can't load namespaces from cluster " + cluster.getName() + ". " + e.getMessage());
        }

        List<Environment> envs = new ArrayList<>();
        Log.info("Namespaces are loaded for " + cluster.getName() + ". Count is " + k8sNamespaces.size() + ". Environments count = " + environments.size());
        for (CloudPassportEnvironment cloudPassportEnvironment : environments) {
            List<Environment> envList = environmentRepository.findByName(cloudPassportEnvironment.name());
            Environment environment = envList.stream()
                    .filter(env -> cluster.getId().equals(env.getClusterId()))
                    .findFirst().orElse(null);
            Log.info("Start working with env = " + cloudPassportEnvironment.name() + " Cluster=" + cluster.getName() + ". Env exists in db? " + (environment != null));
            if (environment == null) {
                environment = new Environment(cloudPassportEnvironment.id(), cloudPassportEnvironment.name());
                environment.setClusterId(cluster.getId());
                environmentRepository.save(environment);
                Log.info("env created in db: " + environment.getName());
            } else {
                Log.info("environment " + environment.getName() + " exists");
            }
            StringBuilder deploymentVersions = new StringBuilder();

            for (CloudPassportNamespace cloudPassportNamespace : cloudPassportEnvironment.namespaces()) {
                Log.info("Start working with namespace = " + cloudPassportNamespace.name());
                V1Namespace v1Namespace = k8sNamespaces.get(cloudPassportNamespace.name());
                List<Namespace> namespaces = namespaceRepository.findByClusterId(cluster.getId());
                Namespace namespace = namespaces.stream()
                        .filter(ns -> cloudPassportNamespace.name().equals(ns.getName()))
                        .findFirst().orElse(null);

                if (namespace == null) {
                    namespace = createNamespace(cloudPassportNamespace, cluster, environment);
                }
                namespace.setExistsInK8s(v1Namespace != null);
                namespaceRepository.save(namespace);
                if (!namespace.getExistsInK8s()) {
                    Log.warn("Namespace " + namespace.getName() + " does not exist in k8s. Skipping it.");
                    continue;
                }
                V1ConfigMap versionsConfigMap = loadVersionsConfigMap(coreV1Api, cloudPassportNamespace.name());
                if (versionsConfigMap == null) {
                    Log.warn("Versions config map not found in namespace " + cloudPassportNamespace.name() + ". Skipping it.");
                    continue;
                }
                Instant configMapCreationTime = versionsConfigMap.getMetadata().getCreationTimestamp().toInstant();
                if (environment.getCleanInstallationDate() == null || environment.getCleanInstallationDate().isBefore(configMapCreationTime)) {
                    Log.info("Setting clean installation date for environment " + environment.getName() + " to " + configMapCreationTime);
                    environment.setCleanInstallationDate(configMapCreationTime);
                }

                String deploymentVersionForNamespace = versionsConfigMap.getData().get(versionsConfigMapDataFieldName);
                if (deploymentVersionForNamespace != null && !deploymentVersionForNamespace.trim().isEmpty() && !deploymentVersions.toString().contains(deploymentVersionForNamespace)) {
                    deploymentVersions.append(deploymentVersionForNamespace).append("\n");
                }
                Log.info("Namespace " + namespace.getName() + " is loaded successfully. Deployment versions are: " + deploymentVersions);
            }
            // Get namespace names from environment's namespace IDs
            List<String> namespaceNames = new ArrayList<>();
            if (environment.getNamespaceIds() != null) {
                for (String nsId : environment.getNamespaceIds()) {
                    namespaceRepository.findByUid(nsId).ifPresent(ns -> namespaceNames.add(ns.getName()));
                }
            }
            environment.setMonitoringData(monitoringService.loadMonitoringData(monitoringUri, environment.getName(), cluster.getName(), namespaceNames));
            environment.setDeploymentVersion(deploymentVersions.toString());
            environmentRepository.save(environment);

            envs.add(environment);
            Log.info("Environment " + environment.getName() + " loaded successfully.");
        }
        return envs;
    }

    private Namespace createNamespace(CloudPassportNamespace cloudPassportNamespace, Cluster cluster, Environment environment) {
        Namespace namespace;
        namespace = new Namespace();
        namespace.setId(cloudPassportNamespace.id());
        namespace.setName(cloudPassportNamespace.name());
        namespace.setClusterId(cluster.getId());
        namespace.setEnvironmentId(environment.getId());
        environment.addNamespaceId(namespace.getId());
        return namespace;
    }

    private V1ConfigMap loadVersionsConfigMap(CoreV1Api coreV1Api, String namespaceName) {
        CoreV1Api.APIlistNamespacedConfigMapRequest request = coreV1Api.listNamespacedConfigMap(namespaceName).fieldSelector("metadata.name=" + versionsConfigMapName);
        V1ConfigMapList configMapList;
        try {
            configMapList = request.execute();
        } catch (ApiException e) {
            throw new IllegalStateException(e);
        }
        if (configMapList.getItems().isEmpty()) {
            Log.warn("No config map with name=" + versionsConfigMapName + " found in namespace " + namespaceName);
            return null;
        }
        return configMapList.getItems().getFirst();
    }


    private String getNameSafely(V1ObjectMeta meta) {
        if (meta == null) {
            return "<empty_name>";
        }
        return meta.getName();
    }

}
