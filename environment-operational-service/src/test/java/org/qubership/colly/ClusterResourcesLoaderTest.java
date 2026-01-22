package org.qubership.colly;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkiverse.wiremock.devservice.WireMockConfigKey;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.qubership.colly.cloudpassport.CloudPassportEnvironment;
import org.qubership.colly.cloudpassport.CloudPassportNamespace;
import org.qubership.colly.cloudpassport.ClusterInfo;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.data.Namespace;
import org.qubership.colly.db.repository.ClusterRepository;
import org.qubership.colly.db.repository.EnvironmentRepository;
import org.qubership.colly.db.repository.NamespaceRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
@ConnectWireMock
// @TestTransaction - removed for Redis
class ClusterResourcesLoaderTest {

    private static final String ENV_1 = "env-1";
    private static final String NAMESPACE_NAME = "namespace1";
    private static final String NAMESPACE_NAME_2 = "namespace2";
    private static final String NAMESPACE_NAME_3 = "namespace3";
    private static final String CLUSTER_NAME = "cluster";
    private static final String CLUSTER_ID = "1";
    private static final ClusterInfo CLOUD_PASSPORT = new ClusterInfo(CLUSTER_ID, CLUSTER_NAME, "42", "https://api.example.com",
            Set.of(createEnvForTests(ENV_1, List.of(new CloudPassportNamespace(NAMESPACE_NAME, NAMESPACE_NAME)))), null);
    private static final OffsetDateTime DATE_2024 = OffsetDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime DATE_2025 = OffsetDateTime.of(2025, 2, 2, 0, 0, 0, 0, ZoneOffset.UTC);
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

    @Inject
    RedisDataSource redisDataSource;
    @Inject
    ClusterRepository clusterRepository;

    private static @NotNull CloudPassportEnvironment createEnvForTests(String name, List<CloudPassportNamespace> namespaces) {
        return new CloudPassportEnvironment(name, name, "some env for tests", namespaces);
    }

    @BeforeEach
    void setUp() throws ApiException {
        coreV1Api = mock(CoreV1Api.class);

        redisDataSource.flushall();
        mockNodesLoading();
        mockAllNamespaceResources();
        wiremock.register(WireMock.get(WireMock.urlPathMatching("/api/v1/query"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[{\"metric\":{},\"value\":[1747924558,\"1\"]}]},\"stats\":{\"seriesFetched\": \"1\",\"executionTimeMsec\":4}}")));
    }

    @Test
    void loadClusterResources_from_cloud_passport() throws ApiException {
        ClusterInfo clusterInfo = new ClusterInfo(CLUSTER_ID, CLUSTER_NAME, "42", "https://api.example.com",
                Set.of(createEnvForTests("env-test", List.of(new CloudPassportNamespace(NAMESPACE_NAME, NAMESPACE_NAME)))), "http://localhost:" + port);
        mockNamespaceLoading("clusterName", List.of(NAMESPACE_NAME));
        mockNodesLoading(new V1Node(), new V1Node());

        String exampleOfLongVersion = "MyVersion 1.0.0MyVersion 1.0.0MyVersion 1.0.0MyVersion 1.0.0MyVersion 1.0.0MyVersion 1.0.0MyVersion 1.0.0MyVersion 1.0.0MyVersion 1.0.0MyVersion 1.0.0MyVersion 1.0.0MyVersion 1.0.0MyVersion 1.0.0MyVersion 1.0.0MyVersion 1.0.0MyVersion 1.0.0MyVersion 1.0.0MyVersion 1.0.0";
        V1ConfigMap configMap = new V1ConfigMap()
                .metadata(new V1ObjectMeta()
                        .name("sd-versions")
                        .uid("configmap-uid")
                        .creationTimestamp(DATE_2024))
                .data(Map.of("solution-descriptors-summary", exampleOfLongVersion));
        mockConfigMaps(List.of(configMap), NAMESPACE_NAME);


        clusterResourcesLoader.loadClusterResources(coreV1Api, clusterInfo);

        List<Environment> envs = environmentRepository.findByName("env-test");
        Environment testEnv = envs.stream().filter(e -> CLUSTER_ID.equals(e.getClusterId())).findFirst().orElseThrow();
        assertThat(testEnv, allOf(
                hasProperty("name", equalTo("env-test")),
                hasProperty("deploymentVersion", equalTo(exampleOfLongVersion + "\n")),
                hasProperty("cleanInstallationDate", equalTo(DATE_2024.toInstant()))));

        assertThat(testEnv.getClusterId(), equalTo(CLUSTER_ID));

        List<Namespace> namespaces = namespaceRepository.findByClusterId(CLUSTER_ID);
        Namespace testNamespace = namespaces.stream().filter(ns -> NAMESPACE_NAME.equals(ns.getName())).findFirst().orElse(null);
        assertThat(testNamespace, hasProperty("name", equalTo(NAMESPACE_NAME)));

        Cluster cluster = clusterRepository.findById(testEnv.getClusterId());
        assertThat(cluster, allOf(
                hasProperty("name", equalTo(CLUSTER_NAME)),
                hasProperty("synced", equalTo(true)),
                hasProperty("numberOfNodes", equalTo(2))));
    }

    @Test
    void load_resources_one_env_several_namespaces() throws ApiException {
        ClusterInfo clusterInfo = new ClusterInfo(CLUSTER_ID, CLUSTER_NAME, "42", "https://api.example.com",
                Set.of(createEnvForTests("env-3-namespaces",
                                List.of(new CloudPassportNamespace(NAMESPACE_NAME, NAMESPACE_NAME),
                                        new CloudPassportNamespace(NAMESPACE_NAME_2, NAMESPACE_NAME_2),
                                        new CloudPassportNamespace(NAMESPACE_NAME_3, NAMESPACE_NAME_3))
                        )
                ), null);
        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME, NAMESPACE_NAME_2, NAMESPACE_NAME_3));

        clusterResourcesLoader.loadClusterResources(coreV1Api, clusterInfo);

        List<Environment> envs = environmentRepository.findByName("env-3-namespaces");
        Environment testEnv = envs.stream().filter(e -> CLUSTER_ID.equals(e.getClusterId())).findFirst().orElse(null);
        assertThat(testEnv, hasProperty("name", equalTo("env-3-namespaces")));
        assertThat(testEnv.getNamespaceIds(), hasSize(3));
        // Check that namespace names exist in repository
        List<Namespace> allNamespaces = namespaceRepository.findByEnvironmentId(testEnv.getId());
        assertThat(allNamespaces, hasItems(
                hasProperty("name", equalTo(NAMESPACE_NAME)),
                hasProperty("name", equalTo(NAMESPACE_NAME_2)),
                hasProperty("name", equalTo(NAMESPACE_NAME_3))));
    }

    @Test
    void load_resources_twice() throws ApiException {
        ClusterInfo clusterInfo = new ClusterInfo(CLUSTER_ID, CLUSTER_NAME, "42", "https://api.example.com",
                Set.of(createEnvForTests("env-3-namespaces",
                        List.of(new CloudPassportNamespace(NAMESPACE_NAME, NAMESPACE_NAME),
                                new CloudPassportNamespace(NAMESPACE_NAME_2, NAMESPACE_NAME_2)))), null);
        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME, NAMESPACE_NAME_2));

        clusterResourcesLoader.loadClusterResources(coreV1Api, clusterInfo);
        List<Environment> envs2 = environmentRepository.findByName("env-3-namespaces");
        Environment testEnv = envs2.stream().filter(e -> CLUSTER_ID.equals(e.getClusterId())).findFirst().orElseThrow();
        assertThat(testEnv.getNamespaceIds(), hasSize(2));
        clusterResourcesLoader.loadClusterResources(coreV1Api, clusterInfo);
        assertThat(testEnv.getNamespaceIds(), hasSize(2));
        List<Namespace> allNamespaces = namespaceRepository.findByEnvironmentId(testEnv.getId());
        assertThat(allNamespaces, hasItems(hasProperty("name", equalTo(NAMESPACE_NAME)), hasProperty("name", equalTo(NAMESPACE_NAME_2))));
    }

    @Test
    void load_resources_for_env_after_update() throws ApiException {
        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME));

        V1ConfigMap configMap = new V1ConfigMap()
                .metadata(new V1ObjectMeta().name("sd-versions").uid("configmap-uid").creationTimestamp(DATE_2024))
                .data(Map.of("solution-descriptors-summary", "MyVersion 1.0.0"));
        mockConfigMaps(List.of(configMap), NAMESPACE_NAME);

        clusterResourcesLoader.loadClusterResources(coreV1Api, CLOUD_PASSPORT);
        List<Environment> envs = environmentRepository.findByName(ENV_1);
        Environment testEnv = envs.stream().filter(e -> CLUSTER_ID.equals(e.getClusterId())).findFirst().orElseThrow();
        assertThat(testEnv.getDeploymentVersion(), equalTo("MyVersion 1.0.0\n"));
        assertThat(testEnv.getCleanInstallationDate(), equalTo(DATE_2024.toInstant()));

        configMap = new V1ConfigMap()
                .metadata(new V1ObjectMeta().name("sd-versions").uid("configmap-uid").creationTimestamp(DATE_2025))
                .data(Map.of("solution-descriptors-summary", "MyVersion 2.0.0"));
        mockConfigMaps(List.of(configMap), NAMESPACE_NAME);

        clusterResourcesLoader.loadClusterResources(coreV1Api, CLOUD_PASSPORT);
        envs = environmentRepository.findByName(ENV_1);
        testEnv = envs.stream().filter(e -> CLUSTER_ID.equals(e.getClusterId())).findFirst().orElseThrow();
        assertThat(testEnv.getDeploymentVersion(), equalTo("MyVersion 2.0.0\n"));
        assertThat(testEnv.getCleanInstallationDate(), equalTo(DATE_2025.toInstant()));
    }

    @Test
    void combine_deployment_version_for_namespaces() throws ApiException {
        ClusterInfo clusterInfo = new ClusterInfo(CLUSTER_ID, CLUSTER_NAME, "42", "https://api.example.com",
                Set.of(createEnvForTests("env-3-namespaces",
                        List.of(new CloudPassportNamespace(NAMESPACE_NAME, NAMESPACE_NAME),
                                new CloudPassportNamespace(NAMESPACE_NAME_2, NAMESPACE_NAME_2),
                                new CloudPassportNamespace(NAMESPACE_NAME_3, NAMESPACE_NAME_3)))), null);
        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME, NAMESPACE_NAME_2, NAMESPACE_NAME_3));

        V1ConfigMap configMap1 = new V1ConfigMap()
                .metadata(new V1ObjectMeta().name("sd-versions").uid("configmap-uid").creationTimestamp(DATE_2025))
                .data(Map.of("solution-descriptors-summary", "MyVersion 1.0.0"));

        mockConfigMaps(List.of(configMap1), NAMESPACE_NAME);

        V1ConfigMap configMap2 = new V1ConfigMap()
                .metadata(new V1ObjectMeta().name("sd-versions").uid("configmap-uid").creationTimestamp(DATE_2024))
                .data(Map.of("solution-descriptors-summary", "MyVersion 2.0.0"));

        mockConfigMaps(List.of(configMap2), NAMESPACE_NAME_2);

        V1ConfigMap configMap3 = new V1ConfigMap()
                .metadata(new V1ObjectMeta().name("sd-versions").uid("configmap-uid").creationTimestamp(DATE_2025))
                .data(Map.of("solution-descriptors-summary", "MyVersion 2.0.0"));

        mockConfigMaps(List.of(configMap3), NAMESPACE_NAME_3);


        clusterResourcesLoader.loadClusterResources(coreV1Api, clusterInfo);
        List<Environment> envs = environmentRepository.findByName("env-3-namespaces");
        Environment testEnv = envs.stream().filter(e -> CLUSTER_ID.equals(e.getClusterId())).findFirst().orElseThrow();
        assertThat(testEnv.getDeploymentVersion(), equalTo("MyVersion 1.0.0\nMyVersion 2.0.0\n"));
        assertThat(testEnv.getCleanInstallationDate(), equalTo(DATE_2025.toInstant()));
    }

    @Test
    void try_to_load_namespace_from_cloud_passport_that_does_not_exist_in_k8s() throws ApiException {
        ClusterInfo clusterInfo = new ClusterInfo(CLUSTER_ID, CLUSTER_NAME, "42", "https://api.example.com",
                Set.of(createEnvForTests("env-2-namespaces",
                        List.of(new CloudPassportNamespace(NAMESPACE_NAME, NAMESPACE_NAME),
                                new CloudPassportNamespace("42", "non-existing-namespace")))), null);
        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME));

        clusterResourcesLoader.loadClusterResources(coreV1Api, clusterInfo);
        List<Environment> envs = environmentRepository.findByName("env-2-namespaces");
        Environment testEnv = envs.stream().filter(e -> CLUSTER_ID.equals(e.getClusterId())).findFirst().orElseThrow();
        assertThat(testEnv.getNamespaceIds(), hasSize(2));
        List<Namespace> allNamespaces = namespaceRepository.findByEnvironmentId(testEnv.getId());
        assertThat(allNamespaces, hasItems(
                allOf(hasProperty("name", equalTo(NAMESPACE_NAME)), hasProperty("existsInK8s", equalTo(true))),
                allOf(hasProperty("name", equalTo("non-existing-namespace")), hasProperty("existsInK8s", equalTo(false)))));
    }

    @Test
    void load_resources_from_unreachable_cluster() throws ApiException {
        ClusterInfo clusterInfo = new ClusterInfo(CLUSTER_ID, "unreachable-cluster", "42", "https://some.unreachable.url",
                Set.of(createEnvForTests("env-unreachable",
                        List.of(new CloudPassportNamespace(NAMESPACE_NAME, NAMESPACE_NAME)))),
                "http://localhost:" + port);

        CoreV1Api.APIlistNamespaceRequest nsRequest = mock(CoreV1Api.APIlistNamespaceRequest.class);
        when(coreV1Api.listNamespace()).thenReturn(nsRequest);
        when(nsRequest.execute()).thenThrow(new ApiException());

        clusterResourcesLoader.loadClusterResources(coreV1Api, clusterInfo);
        List<Environment> envs = environmentRepository.findByName("env-unreachable");
        Environment testEnv = envs.stream().filter(e -> CLUSTER_ID.equals(e.getClusterId())).findFirst().orElse(null);
        assertThat(testEnv, hasProperty("name", equalTo("env-unreachable")));
        assertThat(testEnv.getNamespaceIds(), hasSize(1));
        List<Namespace> allNamespaces = namespaceRepository.findByEnvironmentId(testEnv.getId());
        assertThat(allNamespaces, hasItems(allOf(
                hasProperty("existsInK8s", equalTo(false)),
                hasProperty("name", equalTo(NAMESPACE_NAME)))));
        assertThat(testEnv.getClusterId(), equalTo(CLUSTER_ID));

        Cluster cluster = clusterRepository.findById(testEnv.getClusterId());
        assertThat(cluster, allOf(
                hasProperty("name", equalTo("unreachable-cluster")),
                hasProperty("synced", equalTo(false))));

    }

    @Test
    void load_namespace_that_created_after_first_loading() throws ApiException {
        ClusterInfo clusterInfo = new ClusterInfo(CLUSTER_ID, CLUSTER_NAME, "42", "https://api.example.com",
                Set.of(createEnvForTests("env-with-new-namespace",
                        List.of(new CloudPassportNamespace(NAMESPACE_NAME, NAMESPACE_NAME), new CloudPassportNamespace("42", "new-namespace")))), null);
        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME));

        clusterResourcesLoader.loadClusterResources(coreV1Api, clusterInfo);
        List<Environment> envs = environmentRepository.findByName("env-with-new-namespace");
        Environment testEnv = envs.stream().filter(e -> CLUSTER_ID.equals(e.getClusterId())).findFirst().orElseThrow();
        assertThat(testEnv.getNamespaceIds(), hasSize(2));

        List<Namespace> allNamespaces1 = namespaceRepository.findByEnvironmentId(testEnv.getId());
        assertThat(allNamespaces1, hasItems(
                allOf(hasProperty("name", equalTo(NAMESPACE_NAME)), hasProperty("existsInK8s", equalTo(true))),
                allOf(hasProperty("name", equalTo("new-namespace")), hasProperty("existsInK8s", equalTo(false)))));

        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME, "new-namespace"));
        clusterResourcesLoader.loadClusterResources(coreV1Api, clusterInfo);
        List<Environment> envs2 = environmentRepository.findByName("env-with-new-namespace");
        Environment testEnv2 = envs2.stream().filter(e -> CLUSTER_ID.equals(e.getClusterId())).findFirst().orElseThrow();

        assertThat(testEnv2.getNamespaceIds(), hasSize(2));

        List<Namespace> allNamespaces2 = namespaceRepository.findByEnvironmentId(testEnv2.getId());
        assertThat(allNamespaces2, hasItems(
                allOf(hasProperty("name", equalTo(NAMESPACE_NAME)), hasProperty("existsInK8s", equalTo(true))),
                allOf(hasProperty("name", equalTo("new-namespace")), hasProperty("existsInK8s", equalTo(true)))));

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

    private void mockConfigMaps(List<V1ConfigMap> configMap1, String targetNamespace) throws ApiException {
        V1ConfigMapList configMapList = new V1ConfigMapList().items(configMap1);
        CoreV1Api.APIlistNamespacedConfigMapRequest configMapRequest = mock(CoreV1Api.APIlistNamespacedConfigMapRequest.class);
        when(coreV1Api.listNamespacedConfigMap(targetNamespace)).thenReturn(configMapRequest);
        when(configMapRequest.fieldSelector("metadata.name=" + "sd-versions")).thenReturn(configMapRequest);
        when(configMapRequest.execute()).thenReturn(configMapList);
    }

    private void mockAllNamespaceResources() throws ApiException {
        CoreV1Api.APIlistNamespacedConfigMapRequest configMapRequest = mock(CoreV1Api.APIlistNamespacedConfigMapRequest.class);
        when(coreV1Api.listNamespacedConfigMap(any())).thenReturn(configMapRequest);
        when(configMapRequest.fieldSelector(any())).thenReturn(configMapRequest);
        when(configMapRequest.execute()).thenReturn(new V1ConfigMapList());
    }

    private void mockNamespaceLoading(String clusterName, List<String> namespaceNames) throws ApiException {
        mockNamespaceLoading(clusterName, namespaceNames, Map.of());
    }

    private void mockNamespaceLoading(String clusterName, List<String> namespaceNames, Map<String, String> labels) throws ApiException {
        List<V1Namespace> v1Namespaces = namespaceNames
                .stream()
                .map(namespaceName -> new V1Namespace().metadata(new V1ObjectMeta()
                        .name(namespaceName)
                        .uid(namespaceName + clusterName)
                        .labels(labels)))
                .toList();
        V1NamespaceList nsList = new V1NamespaceList().items(v1Namespaces);

        CoreV1Api.APIlistNamespaceRequest nsRequest = mock(CoreV1Api.APIlistNamespaceRequest.class);
        when(coreV1Api.listNamespace()).thenReturn(nsRequest);
        when(nsRequest.execute()).thenReturn(nsList);
    }

    private void mockNodesLoading(V1Node... nodes) throws ApiException {
        V1NodeList items = new V1NodeList().items(Arrays.stream(nodes).toList());
        CoreV1Api.APIlistNodeRequest nodeRequest = mock(CoreV1Api.APIlistNodeRequest.class);
        when(coreV1Api.listNode()).thenReturn(nodeRequest);
        when(nodeRequest.execute()).thenReturn(items);
    }

}
