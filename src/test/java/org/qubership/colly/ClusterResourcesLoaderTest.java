package org.qubership.colly;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkiverse.wiremock.devservice.WireMockConfigKey;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.colly.cloudpassport.CloudPassport;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;
import org.qubership.colly.cloudpassport.CloudPassportNamespace;
import org.qubership.colly.db.*;
import org.qubership.colly.storage.EnvironmentRepository;
import org.qubership.colly.storage.NamespaceRepository;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.qubership.colly.ClusterResourcesLoader.*;

@QuarkusTest
@ConnectWireMock
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

    WireMock wiremock;
    @ConfigProperty(name = WireMockConfigKey.PORT)
    Integer port;

    CoreV1Api coreV1Api;
    AppsV1Api appsV1Api;

    @BeforeEach
    void setUp() throws ApiException {
        coreV1Api = mock(CoreV1Api.class);
        appsV1Api = mock(AppsV1Api.class);
        mockAllNamespaceResources();
        wiremock.register(WireMock.get(WireMock.urlPathMatching("/api/v1/query"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[{\"metric\":{},\"value\":[1747924558,\"1\"]}]},\"stats\":{\"seriesFetched\": \"1\",\"executionTimeMsec\":4}}")));
    }

    @Test
    @TestTransaction
    void loadClusterResources_from_cloud_passport() throws ApiException {
        CloudPassport cloudPassport = new CloudPassport(CLUSTER_NAME, "42", "https://api.example.com",
                List.of(new CloudPassportEnvironment("env-test", "some env for tests",
                        List.of(new CloudPassportNamespace(NAMESPACE_NAME)))), URI.create("http://localhost:" + port));
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
                .metadata(new V1ObjectMeta().name("sd-versions").uid("configmap-uid"))
                .data(Map.of("solution-descriptors-summary", "MyVersion 1.0.0"));
        mockConfigMaps(List.of(configMap), NAMESPACE_NAME);


        clusterResourcesLoader.loadClusterResources(coreV1Api, appsV1Api, cloudPassport);

        Environment testEnv = environmentRepository.findByNameAndCluster("env-test", CLUSTER_NAME);
        assertThat(testEnv, allOf(
                hasProperty("name", equalTo("env-test")),
                hasProperty("description", equalTo("some env for tests")),
                hasProperty("deploymentVersion", equalTo("MyVersion 1.0.0\n")),
                hasProperty("type", equalTo(EnvironmentType.ENVIRONMENT))));

        assertThat(testEnv.cluster, hasProperty("name", equalTo(CLUSTER_NAME)));

        Namespace testNamespace = namespaceRepository.findByNameAndCluster(NAMESPACE_NAME, CLUSTER_NAME);
        assertThat(testNamespace, hasProperty("name", equalTo(NAMESPACE_NAME)));

//        Pod testPod = testNamespace.pods.getFirst();
//        assertThat(testNamespace.pods, hasSize(1));
//todo        assertThat(testPod, hasProperty("name", equalTo("pod-1")));

//        Deployment testDeployment = testNamespace.deployments.getFirst();
//        assertThat(testNamespace.deployments, hasSize(1));
//todo        assertThat(testDeployment, hasProperty("name", equalTo("dep-1")));

//        ConfigMap testConfigMap = testNamespace.configMaps.getFirst();
//        assertThat(testNamespace.configMaps, hasSize(1));
//todo        assertThat(testConfigMap, hasProperty("name", equalTo("configmap-1")));
    }

    @Test
    @TestTransaction
    void load_resources_one_env_several_namespaces() throws ApiException {
        CloudPassport cloudPassport = new CloudPassport(CLUSTER_NAME, "42", "https://api.example.com",
                List.of(new CloudPassportEnvironment("env-3-namespaces", "some env for tests",
                        List.of(new CloudPassportNamespace(NAMESPACE_NAME),
                                new CloudPassportNamespace(NAMESPACE_NAME_2),
                                new CloudPassportNamespace(NAMESPACE_NAME_3)))), null);
        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME, NAMESPACE_NAME_2, NAMESPACE_NAME_3));

        clusterResourcesLoader.loadClusterResources(coreV1Api, appsV1Api, cloudPassport);

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
                                new CloudPassportNamespace(NAMESPACE_NAME_2)))), null);
        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME, NAMESPACE_NAME_2));

        clusterResourcesLoader.loadClusterResources(coreV1Api, appsV1Api, cloudPassport);
        Environment testEnv = environmentRepository.findByNameAndCluster("env-3-namespaces", CLUSTER_NAME);
        assertThat(testEnv.getNamespaces(), hasSize(2));
        clusterResourcesLoader.loadClusterResources(coreV1Api, appsV1Api, cloudPassport);
        assertThat(testEnv.getNamespaces(), hasSize(2));
        assertThat(testEnv.getNamespaces(), hasItems(hasProperty("name", equalTo(NAMESPACE_NAME)), hasProperty("name", equalTo(NAMESPACE_NAME_2))));
    }

    @Test
    @TestTransaction
    void try_to_load_namespace_from_cloud_passport_that_does_not_exist_in_k8s() throws ApiException {
        CloudPassport cloudPassport = new CloudPassport(CLUSTER_NAME, "42", "https://api.example.com",
                List.of(new CloudPassportEnvironment("env-2-namespaces", "some env for tests",
                        List.of(new CloudPassportNamespace(NAMESPACE_NAME),
                                new CloudPassportNamespace("non-existing-namespace")))), null);
        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME));

        clusterResourcesLoader.loadClusterResources(coreV1Api, appsV1Api, cloudPassport);
        Environment testEnv = environmentRepository.findByNameAndCluster("env-2-namespaces", CLUSTER_NAME);
        assertThat(testEnv.getNamespaces(), hasSize(1));
        assertThat(testEnv.getNamespaces(), hasItems(hasProperty("name", equalTo(NAMESPACE_NAME))));
    }

    //todo add test to check changes in pods, configmaps and deployments between two loads

    @Test
    @TestTransaction
    void try_to_load_resources_from_unreachable_cluster() throws ApiException {
        CloudPassport cloudPassport = new CloudPassport("unreachable-cluster", "42", "https://some.unreachable.url",
                List.of(new CloudPassportEnvironment("env-unreachable", "some env for tests",
                        List.of(new CloudPassportNamespace(NAMESPACE_NAME)))), URI.create("http://localhost:" + port));

        CoreV1Api.APIlistNamespaceRequest nsRequest = mock(CoreV1Api.APIlistNamespaceRequest.class);
        when(coreV1Api.listNamespace()).thenReturn(nsRequest);
        when(nsRequest.execute()).thenThrow(new ApiException());

        clusterResourcesLoader.loadClusterResources(coreV1Api, appsV1Api, cloudPassport);
        Environment testEnv = environmentRepository.findByNameAndCluster("env-unreachable", "unreachable-cluster");
        assertThat(testEnv, hasProperty("name", equalTo("env-unreachable")));
        assertThat(testEnv.getNamespaces(), hasSize(0));
    }

    private void mockConfigMaps(List<V1ConfigMap> configMap1, String targetNamespace) throws ApiException {
        V1ConfigMapList configMapList = new V1ConfigMapList().items(configMap1);
        CoreV1Api.APIlistNamespacedConfigMapRequest configMapRequest = mock(CoreV1Api.APIlistNamespacedConfigMapRequest.class);
        when(coreV1Api.listNamespacedConfigMap(targetNamespace).fieldSelector("metadata.name=" + "sd-versions")).thenReturn(configMapRequest);
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
        List<V1Namespace> v1Namespaces = namespaceNames
                .stream()
                .map(namespaceName -> new V1Namespace().metadata(new V1ObjectMeta()
                        .name(namespaceName)
                        .uid(namespaceName + clusterName)
                        .labels(Map.of(LABEL_DISCOVERY_CLI_IO_LEVEL, LABEL_LEVEL_APPS,
                                LABEL_DISCOVERY_CLI_IO_TYPE, LABEL_TYPE_CORE))))
                .toList();
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
        when(configMapRequest.fieldSelector(any())).thenReturn(configMapRequest);
        when(configMapRequest.execute()).thenReturn(new V1ConfigMapList());
    }


    @Test
    void testHelloEndpoint() {
        Assertions.assertNotNull(wiremock);
        wiremock.register(WireMock.get(WireMock.urlMatching("/api/v1/query?query=*"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("test.json")));

    }
}
