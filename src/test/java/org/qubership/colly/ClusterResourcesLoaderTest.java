package org.qubership.colly;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.colly.cloudpassport.CloudPassport;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;
import org.qubership.colly.cloudpassport.CloudPassportNamespace;
import org.qubership.colly.db.*;
import org.qubership.colly.storage.EnvironmentRepository;
import org.qubership.colly.storage.NamespaceRepository;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
class ClusterResourcesLoaderTest {

    public static final String NAMESPACE_NAME = "namespace1";
    public static final String NAMESPACE_NAME_2 = "namespace2";
    public static final String NAMESPACE_NAME_3 = "namespace3";
    public static final String CLUSTER_NAME = "test-cluster";
    @Inject
    ClusterResourcesLoader clusterResourcesLoader;

    @Inject
    EnvironmentRepository environmentRepository;
    @Inject
    NamespaceRepository namespaceRepository;


    CoreV1Api coreV1Api;
    AppsV1Api appsV1Api;

    @BeforeEach
    void setUp() throws ApiException {
        coreV1Api = mock(CoreV1Api.class);
        appsV1Api = mock(AppsV1Api.class);
        mockAllNamespaceResources();
    }

    @Test
    @TestTransaction
    void loadClusterResources_from_cloud_passport() throws ApiException {
        CloudPassport cloudPassport = new CloudPassport(CLUSTER_NAME, "42", "https://api.example.com",
                List.of(new CloudPassportEnvironment("env-test", "some env for tests",
                        List.of(new CloudPassportNamespace(NAMESPACE_NAME)))));
        mockNamespaceLoading("clusterName", List.of(NAMESPACE_NAME));


        V1Deployment dep = new V1Deployment()
                .metadata(new V1ObjectMeta().name("dep-1").uid("dep-uid"))
                .spec(new V1DeploymentSpec().replicas(2));
        mockDeploymentsLoading(List.of(dep), NAMESPACE_NAME);

        V1Pod pod = new V1Pod()
                .metadata(new V1ObjectMeta().name("pod-1").uid("pod-uid"))
                .status(new V1PodStatus().phase("RUNNING"))
                .spec(new V1PodSpec().containers(List.of(new V1Container().name("container-1"))));
        mockPodsLoading(List.of(pod), NAMESPACE_NAME);

        V1ConfigMap configMap = new V1ConfigMap()
                .metadata(new V1ObjectMeta().name("configmap-1").uid("configmap-uid"))
                .data(Map.of("key1", "value1"));
        mockConfigMaps(List.of(configMap), NAMESPACE_NAME);


        clusterResourcesLoader.loadClusterResources(coreV1Api, appsV1Api, cloudPassport.name(), cloudPassport.environments());

        Environment testEnv = environmentRepository.findByNameAndCluster("env-test", CLUSTER_NAME);
        assertThat(testEnv, hasProperty("name", equalTo("env-test")));

        assertThat(testEnv.cluster, hasProperty("name", equalTo(CLUSTER_NAME)));

        Namespace testNamespace = namespaceRepository.findByNameAndCluster(NAMESPACE_NAME, CLUSTER_NAME);
        assertThat(testNamespace, hasProperty("name", equalTo(NAMESPACE_NAME)));

        Pod testPod = testNamespace.pods.getFirst();
        assertThat(testNamespace.pods, hasSize(1));
        assertThat(testPod, hasProperty("name", equalTo("pod-1")));

        Deployment testDeployment = testNamespace.deployments.getFirst();
        assertThat(testNamespace.deployments, hasSize(1));
        assertThat(testDeployment, hasProperty("name", equalTo("dep-1")));

        ConfigMap testConfigMap = testNamespace.configMaps.getFirst();
        assertThat(testNamespace.configMaps, hasSize(1));
        assertThat(testConfigMap, hasProperty("name", equalTo("configmap-1")));
    }

    @Test
    @TestTransaction
    void load_resources_one_env_several_namespaces() throws ApiException {
        CloudPassport cloudPassport = new CloudPassport(CLUSTER_NAME, "42", "https://api.example.com",
                List.of(new CloudPassportEnvironment("env-3-namespaces", "some env for tests",
                        List.of(new CloudPassportNamespace(NAMESPACE_NAME),
                                new CloudPassportNamespace(NAMESPACE_NAME_2),
                                new CloudPassportNamespace(NAMESPACE_NAME_3)))));
        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME, NAMESPACE_NAME_2, NAMESPACE_NAME_3));

        clusterResourcesLoader.loadClusterResources(coreV1Api, appsV1Api, cloudPassport.name(), cloudPassport.environments());

        Environment testEnv = environmentRepository.findByNameAndCluster("env-3-namespaces", CLUSTER_NAME);
        assertThat(testEnv, hasProperty("name", equalTo("env-3-namespaces")));
        assertThat(testEnv.getNamespaces(), hasSize(3));
        assertThat(testEnv.getNamespaces(), hasItems(
                hasProperty("name", equalTo(NAMESPACE_NAME)),
                hasProperty("name", equalTo(NAMESPACE_NAME_2)),
                hasProperty("name", equalTo(NAMESPACE_NAME_3))));
    }

    @Test
    @TestTransaction
    void load_resources_twice() throws ApiException {
        CloudPassport cloudPassport = new CloudPassport(CLUSTER_NAME, "42", "https://api.example.com",
                List.of(new CloudPassportEnvironment("env-3-namespaces", "some env for tests",
                        List.of(new CloudPassportNamespace(NAMESPACE_NAME),
                                new CloudPassportNamespace(NAMESPACE_NAME_2)))));
        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME, NAMESPACE_NAME_2));

        clusterResourcesLoader.loadClusterResources(coreV1Api, appsV1Api, cloudPassport.name(), cloudPassport.environments());
        Environment testEnv = environmentRepository.findByNameAndCluster("env-3-namespaces", CLUSTER_NAME);
        assertThat(testEnv.getNamespaces(), hasSize(2));
        clusterResourcesLoader.loadClusterResources(coreV1Api, appsV1Api, cloudPassport.name(), cloudPassport.environments());
        assertThat(testEnv.getNamespaces(), hasSize(2));
        assertThat(testEnv.getNamespaces(), hasItems(hasProperty("name", equalTo(NAMESPACE_NAME)), hasProperty("name", equalTo(NAMESPACE_NAME_2))));
    }

    @Test
    @TestTransaction
    void try_to_load_namespace_from_cloud_passport_that_does_not_exist_in_k8s() throws ApiException {
        CloudPassport cloudPassport = new CloudPassport(CLUSTER_NAME, "42", "https://api.example.com",
                List.of(new CloudPassportEnvironment("env-2-namespaces", "some env for tests",
                        List.of(new CloudPassportNamespace(NAMESPACE_NAME),
                                new CloudPassportNamespace("non-existing-namespace")))));
        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME));

        clusterResourcesLoader.loadClusterResources(coreV1Api, appsV1Api, cloudPassport.name(), cloudPassport.environments());
        Environment testEnv = environmentRepository.findByNameAndCluster("env-2-namespaces", CLUSTER_NAME);
        assertThat(testEnv.getNamespaces(), hasSize(1));
        assertThat(testEnv.getNamespaces(), hasItems(hasProperty("name", equalTo(NAMESPACE_NAME))));
    }

    //todo add test to check changes in pods, configmaps and deployments between two loads


    private void mockConfigMaps(List<V1ConfigMap> configMap1, String targetNamespace) throws ApiException {
        V1ConfigMapList configMapList = new V1ConfigMapList().items(configMap1);
        CoreV1Api.APIlistNamespacedConfigMapRequest configMapRequest = mock(CoreV1Api.APIlistNamespacedConfigMapRequest.class);
        when(coreV1Api.listNamespacedConfigMap(targetNamespace)).thenReturn(configMapRequest);
        when(configMapRequest.execute()).thenReturn(configMapList);
    }

    private void mockPodsLoading(List<V1Pod> pod1, String targetNamespace) throws ApiException {
        V1PodList podList = new V1PodList().items(pod1);
        CoreV1Api.APIlistNamespacedPodRequest podRequest = mock(CoreV1Api.APIlistNamespacedPodRequest.class);
        when(coreV1Api.listNamespacedPod(targetNamespace)).thenReturn(podRequest);
        when(podRequest.execute()).thenReturn(podList);
    }

    private void mockDeploymentsLoading(List<V1Deployment> dep1, String targetNamespace) throws ApiException {
        V1DeploymentList depList = new V1DeploymentList().items(dep1);

        AppsV1Api.APIlistNamespacedDeploymentRequest depRequest = mock(AppsV1Api.APIlistNamespacedDeploymentRequest.class);
        when(appsV1Api.listNamespacedDeployment(targetNamespace)).thenReturn(depRequest);
        when(depRequest.execute()).thenReturn(depList);
    }

    private void mockNamespaceLoading(String clusterName, List<String> namespaceNames) throws ApiException {
        List<V1Namespace> v1Namespaces = namespaceNames.stream().map(namespaceName -> new V1Namespace().metadata(new V1ObjectMeta().name(namespaceName).uid(namespaceName + clusterName))).toList();
        V1NamespaceList nsList = new V1NamespaceList().items(v1Namespaces);

        CoreV1Api.APIlistNamespaceRequest nsRequest = mock(CoreV1Api.APIlistNamespaceRequest.class);
        when(coreV1Api.listNamespace()).thenReturn(nsRequest);
        when(nsRequest.execute()).thenReturn(nsList);
    }

    private void mockAllNamespaceResources() throws ApiException {
        AppsV1Api.APIlistNamespacedDeploymentRequest depRequest = mock(AppsV1Api.APIlistNamespacedDeploymentRequest.class);
        when(appsV1Api.listNamespacedDeployment(any())).thenReturn(depRequest);
        when(depRequest.execute()).thenReturn(new V1DeploymentList());

        CoreV1Api.APIlistNamespacedPodRequest podRequest = mock(CoreV1Api.APIlistNamespacedPodRequest.class);
        when(coreV1Api.listNamespacedPod(any())).thenReturn(podRequest);
        when(podRequest.execute()).thenReturn(new V1PodList());

        CoreV1Api.APIlistNamespacedConfigMapRequest configMapRequest = mock(CoreV1Api.APIlistNamespacedConfigMapRequest.class);
        when(coreV1Api.listNamespacedConfigMap(any())).thenReturn(configMapRequest);
        when(configMapRequest.execute()).thenReturn(new V1ConfigMapList());
    }
}
