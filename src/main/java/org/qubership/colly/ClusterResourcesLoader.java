package org.qubership.colly;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
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
import org.qubership.colly.data.CloudPassport;
import org.qubership.colly.db.*;
import org.qubership.colly.storage.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@ApplicationScoped
public class ClusterResourcesLoader {

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
            loadClusterResources(client, cloudPassport.name());
        } catch (IOException e) {
            throw new RuntimeException("Unable to create client for cluster - " + cloudPassport, e);
        }
    }

    @Transactional
    public void loadClusterResources(KubeConfig kubeConfig) {
        Log.info("[INFO] loading kubeconfig: " + kubeConfig.getServer());
        try {
            ApiClient client = ClientBuilder.kubeconfig(kubeConfig).build();
            loadClusterResources(client, parseClusterName(kubeConfig));
        } catch (IOException e) {
            throw new RuntimeException("Can't load kubeconfig - " + kubeConfig.getCurrentContext(), e);
        }
    }

    private void loadClusterResources(ApiClient client, String clusterName) {
        Configuration.setDefaultApiClient(client);
        CoreV1Api api = new CoreV1Api();

        Cluster cluster = clusterRepository.findByName(clusterName);
        if (cluster == null) {
            cluster = new Cluster(clusterName);
        }

        //it is required to set links to cluster only if it was saved to db. so need to invoke persist two
        clusterRepository.persist(cluster);
        cluster.environments = loadEnvironments(api, cluster);
        clusterRepository.persist(cluster);
    }

    private List<Environment> loadEnvironments(CoreV1Api api, Cluster cluster) {
        CoreV1Api.APIlistNamespaceRequest apilistNamespaceRequest = api.listNamespace();

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
                namespace.updateDeployments(loadDeployments(v1Namespace.getMetadata().getName()));
                namespace.updateConfigMaps(loadConfigMaps(v1Namespace.getMetadata().getName()));
                namespace.updatePods(loadPods(v1Namespace.getMetadata().getName()));
                namespace.cluster = cluster;
                namespaceRepository.persist(namespace);

                String environmentName = v1Namespace.getMetadata().getLabels().getOrDefault("environmentName", v1Namespace.getMetadata().getName());

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

    private List<Pod> loadPods(String namespaceName) {
        CoreV1Api api = new CoreV1Api();
        CoreV1Api.APIlistNamespacedPodRequest request = api.listNamespacedPod(namespaceName);
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

    private List<ConfigMap> loadConfigMaps(String namespaceName) {
        CoreV1Api api = new CoreV1Api();
        CoreV1Api.APIlistNamespacedConfigMapRequest request = api.listNamespacedConfigMap(namespaceName);
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

    private List<Deployment> loadDeployments(String namespaceName) {

        AppsV1Api appsV1Api = new AppsV1Api();

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
}
