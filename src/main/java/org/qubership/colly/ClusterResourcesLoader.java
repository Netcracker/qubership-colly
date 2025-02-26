package org.qubership.colly;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.NotNull;
import org.qubership.colly.data.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class ClusterResourcesLoader {

    @Inject
    KubeConfigLoader kubeConfigLoader;


    public List<Cluster> loadClusters() {
        List<Cluster> result = new ArrayList<>();
        List<KubeConfig> kubeConfigs = kubeConfigLoader.loadKubeConfigs();
        kubeConfigs.forEach(kubeConfig -> result.add(loadClusterResources(kubeConfig)));
        return result;
    }

    private Cluster loadClusterResources(KubeConfig kubeConfig) {

        try {
            ApiClient client = ClientBuilder.kubeconfig(kubeConfig).build();
            Configuration.setDefaultApiClient(client);
        } catch (IOException e) {
            throw new RuntimeException("Can't load kubeconfig - " + kubeConfig.getCurrentContext(), e);
        }
        CoreV1Api api = new CoreV1Api();
        List<Namespace> namespaces = loadNamespaces(kubeConfig, api);
        return new Cluster(kubeConfig.getCurrentContext(), namespaces);
    }

    @NotNull
    private List<Namespace> loadNamespaces(KubeConfig kubeConfig, CoreV1Api api) {
        CoreV1Api.APIlistNamespaceRequest apilistNamespaceRequest = api.listNamespace();
        List<Namespace> namespaces;
        try {
            V1NamespaceList list = apilistNamespaceRequest.execute();
            namespaces = list.getItems().stream()
                    .map(v1Namespace ->
                            new Namespace(getNameSafely(v1Namespace.getMetadata()),
                                    v1Namespace.getMetadata().getUid(),
                                    "",
                                    loadDeployments(v1Namespace.getMetadata().getName()),
                                    loadConfigMaps(v1Namespace.getMetadata().getName()),
                                    loadPods(v1Namespace.getMetadata().getName()))
                    )
                    .collect(Collectors.toList());

            Log.debug("Loaded " + namespaces.size() + " namespaces for cluster = " + kubeConfig.getCurrentContext());

        } catch (ApiException e) {
            throw new RuntimeException("Can't load resources from cluster - " + kubeConfig.getCurrentContext(), e);
        }
        return namespaces;
    }

    private List<Pod> loadPods(String namespaceName) {
        CoreV1Api api = new CoreV1Api();
        CoreV1Api.APIlistNamespacedPodRequest request = api.listNamespacedPod(namespaceName);
        List<Pod> pods;
        try {
            V1PodList execute = request.execute();
            pods = execute.getItems().stream()
                    .map(v1Pod ->new Pod(v1Pod.getMetadata().getName(),
                            v1Pod.getStatus().getPhase(),
                            v1Pod.toJson()))
                    .toList();
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
        Log.debug("Loaded " + pods.size() + " pods for namespace = " + namespaceName);
        return pods;
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
        List<ConfigMap> configMaps = configMapList.getItems().stream().map(v1ConfigMap ->
                new ConfigMap(getNameSafely(v1ConfigMap.getMetadata()), v1ConfigMap.toJson())).toList();
        Log.debug("Loaded " + configMaps.size() + " config maps for namespace = " + namespaceName);
        return configMaps;
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
                .map(v1Deployment -> new Deployment(
                        getNameSafely(v1Deployment.getMetadata()),
                        v1Deployment.getSpec().getReplicas(),
                        v1Deployment.toJson()))
                .toList());
        Log.debug("Loaded " + deployments.size() + " deployments for namespace = " + namespaceName);
        return deployments;
    }

    private String getNameSafely(V1ObjectMeta meta) {
        if (meta == null) {
            return "<empty_name>";
        }
        return meta.getName();
    }
}
