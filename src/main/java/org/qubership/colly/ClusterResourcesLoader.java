package org.qubership.colly;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.util.credentials.AccessTokenAuthentication;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.commons.compress.utils.Lists;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.qubership.colly.cloudpassport.CloudPassport;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;
import org.qubership.colly.cloudpassport.CloudPassportNamespace;
import org.qubership.colly.db.*;
import org.qubership.colly.monitoring.MonitoringService;
import org.qubership.colly.storage.*;

import java.io.IOException;
import java.net.URI;
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

    @Inject
    NamespaceRepository namespaceRepository;
    @Inject
    ClusterRepository clusterRepository;
    @Inject
    EnvironmentRepository environmentRepository;
    @Inject
    DeploymentRepository deploymentRepository;
    @Inject
    ConfigMapRepository configMapRepository;
    @Inject
    PodRepository podRepository;
    @Inject
    EnvironmentResolverStrategy environmentResolverStrategy;
    @Inject
    MonitoringService monitoringService;

    @ConfigProperty(name = "colly.config-map.versions.name")
    String versionsConfigMapName;

    @ConfigProperty(name = "colly.config-map.versions.data-field-name")
    String versionsConfigMapDataFieldName;


    public static String parseClusterName(KubeConfig kubeConfig) {
        Map<String, String> o = (Map<String, String>) kubeConfig.getClusters().getFirst();
        String name = o.get("name");
        Log.info("[INFO] true cluster name: " + name);
        return name;
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
            AppsV1Api appsV1Api = new AppsV1Api(client);
            loadClusterResources(coreV1Api, appsV1Api, cloudPassport);
        } catch (RuntimeException | IOException e) {
            Log.error("Can't load resources from cluster " + cloudPassport.name(), e);
        }
    }

    @Transactional
    public void loadClusterResources(KubeConfig kubeConfig) {
        Log.info("[INFO] loading kubeconfig: " + kubeConfig.getServer());
        try {
            ApiClient client = ClientBuilder.kubeconfig(kubeConfig).build();
            CoreV1Api coreV1Api = new CoreV1Api(client);
            AppsV1Api appsV1Api = new AppsV1Api(client);
            String clusterName = parseClusterName(kubeConfig);
            Cluster cluster = clusterRepository.findByName(clusterName);
            if (cluster == null) {
                cluster = new Cluster(clusterName);
                Log.info("Cluster " + clusterName + " not found in db. Creating new one.");
                clusterRepository.persist(cluster);
            }

            //it is required to set links to cluster only if it was saved to db. so need to invoke persist two
            cluster.environments = loadEnvironments(coreV1Api, appsV1Api, cluster);
            clusterRepository.persist(cluster);
        } catch (IOException e) {
            throw new RuntimeException("Can't load kubeconfig - " + kubeConfig.getCurrentContext(), e);
        }
    }

    //for testing purposes
    void loadClusterResources(CoreV1Api coreV1Api, AppsV1Api appsV1Api, CloudPassport cloudPassport) {
        Cluster cluster = clusterRepository.findByName(cloudPassport.name());
        if (cluster == null) {
            cluster = new Cluster(cloudPassport.name());
            Log.info("Cluster " + cloudPassport.name() + " not found in db. Creating new one.");
            clusterRepository.persist(cluster);
        }

        //it is required to set links to cluster only if it was saved to db. so need to invoke persist two
        cluster.environments = loadEnvironments(coreV1Api, appsV1Api, cluster, cloudPassport.environments(), cloudPassport.monitoringUrl());
        clusterRepository.persist(cluster);
        Log.info("Cluster " + cloudPassport.name() + " loaded successfully.");
    }

    private List<Environment> loadEnvironments(CoreV1Api coreV1Api, AppsV1Api appsV1Api, Cluster cluster, List<CloudPassportEnvironment> environments, URI monitoringUri) {

        CoreV1Api.APIlistNamespaceRequest apilistNamespaceRequest = coreV1Api.listNamespace();
        Map<String, V1Namespace> k8sNamespaces;
        try {
            V1NamespaceList list = apilistNamespaceRequest.execute();
            k8sNamespaces = list.getItems().stream().collect(Collectors.toMap(v1Namespace -> getNameSafely(v1Namespace.getMetadata()), Function.identity()));

        } catch (ApiException e) {
            k8sNamespaces = new HashMap<>();
            Log.error("Can't load namespaces from cluster " + cluster.name + ". " + e.getMessage());
        }

        List<Environment> envs = new ArrayList<>();
        Log.info("Namespaces are loaded for " + cluster.name + ". Count is " + k8sNamespaces.size() + ". Environments count = " + environments.size());
        for (CloudPassportEnvironment cloudPassportEnvironment : environments) {
            Environment environment = environmentRepository.findByNameAndCluster(cloudPassportEnvironment.name(), cluster.name);
            Log.info("Start working with env = " + cloudPassportEnvironment.name());
            EnvironmentType environmentType;
            if (environment == null) {
                environment = new Environment(cloudPassportEnvironment.name());
                environment.description = cloudPassportEnvironment.description();
                environment.cluster = cluster;
                environmentType = EnvironmentType.UNDEFINED;
                environmentRepository.persist(environment);
                Log.info("env created in db: " + environment.name);
            } else {
                environmentType = environment.type;
                Log.info("environment " + environment.name + " exists");
            }
            StringBuilder deploymentVersions = new StringBuilder();

            for (CloudPassportNamespace cloudPassportNamespace : cloudPassportEnvironment.namespaceDtos()) {
                V1Namespace v1Namespace = k8sNamespaces.get(cloudPassportNamespace.name());
                if (v1Namespace == null) {
                    Log.warn("Namespace with name=" + cloudPassportNamespace.name() + " not found in cluster " + cluster.name + ". Skipping it.");
                    continue;
                }
                String namespaceUid = v1Namespace.getMetadata().getUid();
                Namespace namespace = namespaceRepository.findByUid(namespaceUid);
                if (namespace == null) {
                    namespace = new Namespace();
                    namespace.uid = namespaceUid;
                    namespace.cluster = cluster;
                    namespace.environment = environment;
                    environment.addNamespace(namespace);
                    environmentType = calculateEnvironmentType(v1Namespace, environmentType);
                }
                namespace.name = cloudPassportNamespace.name();
                deploymentVersions.append(loadInformationAboutDeploymentVersion(coreV1Api, cloudPassportNamespace.name()));
//                namespace.updateDeployments(loadDeployments(appsV1Api, v1Namespace.getMetadata().getName()));
//                namespace.updateConfigMaps(loadConfigMaps(coreV1Api, v1Namespace.getMetadata().getName()));
//                namespace.updatePods(loadPods(coreV1Api, v1Namespace.getMetadata().getName()));
                namespaceRepository.persist(namespace);
            }
            environment.monitoringData = monitoringService.loadMonitoringData(monitoringUri, environment.getNamespaces().stream().map(namespace -> namespace.name).toList());
            environment.type = environmentType;
            environment.deploymentVersion = deploymentVersions.toString();
            environmentRepository.persist(environment);

            envs.add(environment);

        }
        return envs;
    }

    private List<Environment> loadEnvironments(CoreV1Api coreV1Api, AppsV1Api appsV1Api, Cluster cluster) {
        CoreV1Api.APIlistNamespaceRequest apilistNamespaceRequest = coreV1Api.listNamespace();

        List<Environment> environments = new ArrayList<>();
        try {
            V1NamespaceList list = apilistNamespaceRequest.execute();
            for (V1Namespace v1Namespace : list.getItems()) {
                String namespaceUid = v1Namespace.getMetadata().getUid();
                Namespace namespace = namespaceRepository.findByUid(namespaceUid);
                if (namespace == null) {
                    namespace = new Namespace();
                    namespace.uid = namespaceUid;
                }
                namespace.name = getNameSafely(v1Namespace.getMetadata());
                namespace.updateDeployments(loadDeployments(appsV1Api, v1Namespace.getMetadata().getName()));
                namespace.updateConfigMaps(loadConfigMaps(coreV1Api, v1Namespace.getMetadata().getName()));
                namespace.updatePods(loadPods(coreV1Api, v1Namespace.getMetadata().getName()));
                namespace.cluster = cluster;
                namespaceRepository.persist(namespace);

                String environmentName = environmentResolverStrategy.resolveEnvironmentName(v1Namespace);

                Environment environment = environmentRepository.findByNameAndCluster(environmentName, cluster.name);
                if (environment == null) {

                    Optional<Environment> environmentOpt = environments.stream()
                            .filter(env -> env.name.equals(environmentName))
                            .findFirst();

                    if (environmentOpt.isEmpty()) {
                        environment = new Environment(environmentName);
                        environments.add(environment);
                        environment.cluster = cluster;
                    } else {
                        environment = environmentOpt.get();
                    }
                    environment.addNamespace(namespace);
                } else {
                    List<Namespace> list1 = environment.getNamespaces().stream().filter(ns -> ns.uid.equals(namespaceUid)).toList();
                    if (list1.isEmpty()) {
                        environment.addNamespace(namespace);
                    }
                }
                namespace.environment = environment;
                environmentRepository.persist(environment);
            }
        } catch (ApiException e) {
            throw new RuntimeException("Can't load resources from cluster " + cluster.name, e);
        }


        return environments;
    }

    private List<Pod> loadPods(CoreV1Api coreV1Api, String namespaceName) {
        CoreV1Api.APIlistNamespacedPodRequest request = coreV1Api.listNamespacedPod(namespaceName);
        List<Pod> pods;
        try {
            V1PodList execute = request.execute();
            pods = execute.getItems().stream()
                    .map(this::createOrUpdatePod)
                    .toList();
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
        Log.debug("Loaded " + pods.size() + " pods for namespace = " + namespaceName);
        return pods;
    }

    private Pod createOrUpdatePod(V1Pod v1Pod) {
        String uid = v1Pod.getMetadata().getUid();
        Pod pod = podRepository.findByUid(uid);
        if (pod == null) {
            pod = new Pod();
            pod.uid = uid;
        }
        pod.name = getNameSafely(v1Pod.getMetadata());
        pod.status = v1Pod.getStatus().getPhase();
        pod.configuration = v1Pod.toJson();
        return pod;
    }

    private String loadInformationAboutDeploymentVersion(CoreV1Api coreV1Api, String namespaceName) {
        CoreV1Api.APIlistNamespacedConfigMapRequest request = coreV1Api.listNamespacedConfigMap(namespaceName).fieldSelector("metadata.name=" + versionsConfigMapName);
        V1ConfigMapList configMapList;
        try {
            configMapList = request.execute();
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
        if (configMapList.getItems().isEmpty()) {
            Log.warn("No config map with name=" + versionsConfigMapName + " found in namespace " + namespaceName);
            return "";
        }
        V1ConfigMap configMap = configMapList.getItems().getFirst();
        return configMap.getData().get(versionsConfigMapDataFieldName) + "\n";
    }

    private List<ConfigMap> loadConfigMaps(CoreV1Api coreV1Api, String namespaceName) {
        CoreV1Api.APIlistNamespacedConfigMapRequest request = coreV1Api.listNamespacedConfigMap(namespaceName);
        V1ConfigMapList configMapList;
        try {
            configMapList = request.execute();
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
        List<ConfigMap> configMaps = configMapList.getItems().stream()
                .map(this::createOrUpdateConfigMap)
                .toList();
        Log.debug("Loaded " + configMaps.size() + " config maps for namespace = " + namespaceName);
        return configMaps;
    }

    private ConfigMap createOrUpdateConfigMap(V1ConfigMap v1ConfigMap) {
        String uid = v1ConfigMap.getMetadata().getUid();
        ConfigMap configMap = configMapRepository.findByUid(uid);
        if (configMap == null) {
            configMap = new ConfigMap();
            configMap.uid = uid;
        }
        configMap.name = getNameSafely(v1ConfigMap.getMetadata());
        configMap.content = v1ConfigMap.getData();
        configMap.configuration = v1ConfigMap.toJson();
        return configMap;
    }

    private List<Deployment> loadDeployments(AppsV1Api appsV1Api, String namespaceName) {

        AppsV1Api.APIlistNamespacedDeploymentRequest request = appsV1Api.listNamespacedDeployment(namespaceName);

        List<Deployment> deployments = Lists.newArrayList();
        V1DeploymentList deploymentList;
        try {
            deploymentList = request.execute();
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
        deployments.addAll(deploymentList.getItems().stream()
                .map(this::createOrGetDeployment)
                .toList());
        Log.debug("Loaded " + deployments.size() + " deployments for namespace = " + namespaceName);
        return deployments;
    }

    private Deployment createOrGetDeployment(V1Deployment v1Deployment) {
        String uid = v1Deployment.getMetadata().getUid();
        Deployment deployment = deploymentRepository.findByUid(uid);
        if (deployment == null) {
            deployment = new Deployment();
            deployment.uid = uid;
        }
        deployment.name = getNameSafely(v1Deployment.getMetadata());
        deployment.replicas = v1Deployment.getSpec().getReplicas();
        deployment.configuration = v1Deployment.toJson();
        return deployment;
    }

    private String getNameSafely(V1ObjectMeta meta) {
        if (meta == null) {
            return "<empty_name>";
        }
        return meta.getName();
    }

    private EnvironmentType calculateEnvironmentType(V1Namespace v1Namespace, EnvironmentType defaultEnvType) {
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
