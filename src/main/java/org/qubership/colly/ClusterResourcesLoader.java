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
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.qubership.colly.cloudpassport.CloudPassport;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;
import org.qubership.colly.cloudpassport.CloudPassportNamespace;
import org.qubership.colly.db.ClusterRepository;
import org.qubership.colly.db.EnvironmentRepository;
import org.qubership.colly.db.NamespaceRepository;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.data.EnvironmentType;
import org.qubership.colly.db.data.Namespace;
import org.qubership.colly.monitoring.MonitoringService;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@ApplicationScoped
public class ClusterResourcesLoader {

    static final String LABEL_DISCOVERY_CLI_IO_LEVEL = "discovery.cli.io/level";
    static final String LABEL_DISCOVERY_CLI_IO_TYPE = "discovery.cli.io/type";
    static final String LABEL_LEVEL_INFRA = "infra";
    static final String LABEL_LEVEL_APPS = "apps";
    static final String LABEL_TYPE_CORE = "core";
    static final String LABEL_TYPE_CSE_TOOLSET = "cse-toolset";

    private final NamespaceRepository namespaceRepository;
    private final ClusterRepository clusterRepository;
    private final EnvironmentRepository environmentRepository;
    private final MonitoringService monitoringService;

    @ConfigProperty(name = "colly.config-map.versions.name")
    String versionsConfigMapName;

    @ConfigProperty(name = "colly.config-map.versions.data-field-name")
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


    @Transactional
    public void loadClusterResources(CloudPassport cloudPassport) {
        AccessTokenAuthentication authentication = new AccessTokenAuthentication(cloudPassport.token());
        try {
            ApiClient client = ClientBuilder.standard()
                    .setAuthentication(authentication)
                    .setBasePath(cloudPassport.cloudApiHost())
                    .setVerifyingSsl(false)
                    .build();
            CoreV1Api coreV1Api = new CoreV1Api(client);
            loadClusterResources(coreV1Api, cloudPassport);
        } catch (RuntimeException | IOException e) {
            Log.error("Can't load resources from cluster " + cloudPassport.name(), e);
        }
    }

    //for testing purposes
    void loadClusterResources(CoreV1Api coreV1Api, CloudPassport cloudPassport) {
        Log.info("Start Loading cluster resources for: " + cloudPassport.name());
        Cluster cluster = clusterRepository.findByName(cloudPassport.name());
        if (cluster == null) {
            cluster = new Cluster(cloudPassport.name());
            Log.info("Cluster " + cloudPassport.name() + " not found in db. Creating new one.");
            clusterRepository.persist(cluster);
        }

        //it is required to set links to cluster only if it was saved to db. so need to invoke persist two
        cluster.environments = loadEnvironments(coreV1Api, cluster, cloudPassport.environments(), cloudPassport.monitoringUrl());
        clusterRepository.persist(cluster);
        Log.info("Cluster " + cloudPassport.name() + " loaded successfully.");
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
            Environment environment = environmentRepository.findByNameAndCluster(cloudPassportEnvironment.name(), cluster.getName());
            Log.info("Start working with env = " + cloudPassportEnvironment.name() + " Cluster=" + cluster.getName() + ". Env exists in db? " + (environment != null));
            EnvironmentType environmentType;
            if (environment == null) {
                environment = new Environment(cloudPassportEnvironment.name());
                environment.setDescription(cloudPassportEnvironment.description());
                environment.setCluster(cluster);
                environmentType = EnvironmentType.UNDEFINED;
                environmentRepository.persist(environment);
                Log.info("env created in db: " + environment.getName());
            } else {
                environmentType = environment.getType();
                Log.info("environment " + environment.getName() + " exists");
            }
            StringBuilder deploymentVersions = new StringBuilder();

            for (CloudPassportNamespace cloudPassportNamespace : cloudPassportEnvironment.namespaceDtos()) {
                Log.info("Start working with namespace = " + cloudPassportNamespace.name());
                V1Namespace v1Namespace = k8sNamespaces.get(cloudPassportNamespace.name());
                Namespace namespace = namespaceRepository.findByNameAndCluster(cloudPassportNamespace.name(), cluster.getName());

                if (v1Namespace == null) {
                    Log.warn("Namespace with name=" + cloudPassportNamespace.name() + " is not found in cluster " + cluster.getName());
                    if (namespace == null) {
                        namespace = createNamespace(UUID.randomUUID().toString(), cluster, environment);
                    }
                    namespace.setExistsInK8s(false);
                } else {
                    if (namespace == null) {
                        namespace = createNamespace(v1Namespace.getMetadata().getUid(), cluster, environment);
                        environmentType = calculateEnvironmentType(v1Namespace, environmentType);
                    }
                    namespace.setExistsInK8s(true);
                }
                namespace.setName(cloudPassportNamespace.name());
                namespaceRepository.persist(namespace);
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
            environment.setMonitoringData(monitoringService.loadMonitoringData(monitoringUri, environment.getNamespaces().stream().map(Namespace::getName).toList()));
            environment.setType(environmentType);
            environment.setDeploymentVersion(deploymentVersions.toString());
            environmentRepository.persist(environment);

            envs.add(environment);
            Log.info("Environment " + environment.getName() + " loaded successfully.");
        }
        return envs;
    }

    private Namespace createNamespace(String uuid, Cluster cluster, Environment environment) {
        Namespace namespace;
        namespace = new Namespace();
        namespace.setUid(uuid);
        namespace.setCluster(cluster);
        namespace.setEnvironment(environment);
        environment.addNamespace(namespace);
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

    private EnvironmentType calculateEnvironmentType(V1Namespace v1Namespace, EnvironmentType defaultEnvType) {
        if (v1Namespace == null) {
            return defaultEnvType;
        }
        Map<String, String> labels = Objects.requireNonNull(v1Namespace.getMetadata()).getLabels();
        String levelValue = labels.get(LABEL_DISCOVERY_CLI_IO_LEVEL);
        if (LABEL_LEVEL_APPS.equals(levelValue)) {
            String typeValue = labels.get(LABEL_DISCOVERY_CLI_IO_TYPE);
            if (LABEL_TYPE_CORE.equals(typeValue)) {
                return EnvironmentType.ENVIRONMENT;
            }
            if (LABEL_TYPE_CSE_TOOLSET.equals(typeValue)) {
                return EnvironmentType.CSE_TOOLSET;
            }
            return EnvironmentType.ENVIRONMENT;
        }
        if (LABEL_LEVEL_INFRA.equals(levelValue)) {
            return EnvironmentType.INFRASTRUCTURE;
        }
        return defaultEnvType;
    }
}
