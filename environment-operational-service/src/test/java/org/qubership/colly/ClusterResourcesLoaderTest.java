package org.qubership.colly;

import com.github.tomakehurst.wiremock.client.WireMock;
import io.kubernetes.client.openapi.ApiException;
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
import org.qubership.colly.db.EnvironmentRepository;
import org.qubership.colly.db.NamespaceRepository;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.data.EnvironmentType;
import org.qubership.colly.db.data.Namespace;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.qubership.colly.ClusterResourcesLoader.*;

@QuarkusTest
@ConnectWireMock
@TestTransaction
class ClusterResourcesLoaderTest {

    private static final String ENV_1 = "env-1";
    private static final String NAMESPACE_NAME = "namespace1";
    private static final String NAMESPACE_NAME_2 = "namespace2";
    private static final String NAMESPACE_NAME_3 = "namespace3";
    private static final String CLUSTER_NAME = "cluster";
    private static final CloudPassport CLOUD_PASSPORT = new CloudPassport(CLUSTER_NAME, "42", "https://api.example.com",
            Set.of(new CloudPassportEnvironment(ENV_1, "some env for tests",
                    List.of(new CloudPassportNamespace(NAMESPACE_NAME)))), null);
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

    @BeforeEach
    void setUp() throws ApiException {
        coreV1Api = mock(CoreV1Api.class);
        mockAllNamespaceResources();
        wiremock.register(WireMock.get(WireMock.urlPathMatching("/api/v1/query"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[{\"metric\":{},\"value\":[1747924558,\"1\"]}]},\"stats\":{\"seriesFetched\": \"1\",\"executionTimeMsec\":4}}")));
    }

    @Test
    void loadClusterResources_from_cloud_passport() throws ApiException {
        CloudPassport cloudPassport = new CloudPassport(CLUSTER_NAME, "42", "https://api.example.com",
                Set.of(new CloudPassportEnvironment("env-test", "some env for tests",
                        List.of(new CloudPassportNamespace(NAMESPACE_NAME)))), URI.create("http://localhost:" + port));
        mockNamespaceLoading("clusterName", List.of(NAMESPACE_NAME));

        V1ConfigMap configMap = new V1ConfigMap()
                .metadata(new V1ObjectMeta()
                        .name("sd-versions")
                        .uid("configmap-uid")
                        .creationTimestamp(DATE_2024))
                .data(Map.of("solution-descriptors-summary", "MyVersion 1.0.0"));
        mockConfigMaps(List.of(configMap), NAMESPACE_NAME);


        clusterResourcesLoader.loadClusterResources(coreV1Api, cloudPassport);

        Environment testEnv = environmentRepository.findByNameAndCluster("env-test", CLUSTER_NAME);
        assertThat(testEnv, allOf(
                hasProperty("name", equalTo("env-test")),
                hasProperty("description", equalTo("some env for tests")),
                hasProperty("deploymentVersion", equalTo("MyVersion 1.0.0\n")),
                hasProperty("cleanInstallationDate", equalTo(DATE_2024.toInstant())),
                hasProperty("type", equalTo(EnvironmentType.ENVIRONMENT))));

        assertThat(testEnv.getCluster(), allOf(
                hasProperty("name", equalTo(CLUSTER_NAME)),
                hasProperty("synced", equalTo(true))));

        Namespace testNamespace = namespaceRepository.findByNameAndCluster(NAMESPACE_NAME, CLUSTER_NAME);
        assertThat(testNamespace, hasProperty("name", equalTo(NAMESPACE_NAME)));


    }

    @Test
    void load_resources_one_env_several_namespaces() throws ApiException {
        CloudPassport cloudPassport = new CloudPassport(CLUSTER_NAME, "42", "https://api.example.com",
                Set.of(new CloudPassportEnvironment("env-3-namespaces", "some env for tests",
                        List.of(new CloudPassportNamespace(NAMESPACE_NAME),
                                new CloudPassportNamespace(NAMESPACE_NAME_2),
                                new CloudPassportNamespace(NAMESPACE_NAME_3)))), null);
        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME, NAMESPACE_NAME_2, NAMESPACE_NAME_3));

        clusterResourcesLoader.loadClusterResources(coreV1Api, cloudPassport);

        Environment testEnv = environmentRepository.findByNameAndCluster("env-3-namespaces", CLUSTER_NAME);
        assertThat(testEnv, hasProperty("name", equalTo("env-3-namespaces")));
        assertThat(testEnv.getNamespaces(), hasSize(3));
        assertThat(testEnv.getNamespaces(), hasItems(
                hasProperty("name", equalTo(NAMESPACE_NAME)),
                hasProperty("name", equalTo(NAMESPACE_NAME_2)),
                hasProperty("name", equalTo(NAMESPACE_NAME_3))));
    }

    @Test
    void load_resources_twice() throws ApiException {
        CloudPassport cloudPassport = new CloudPassport(CLUSTER_NAME, "42", "https://api.example.com",
                Set.of(new CloudPassportEnvironment("env-3-namespaces", "some env for tests",
                        List.of(new CloudPassportNamespace(NAMESPACE_NAME),
                                new CloudPassportNamespace(NAMESPACE_NAME_2)))), null);
        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME, NAMESPACE_NAME_2));

        clusterResourcesLoader.loadClusterResources(coreV1Api, cloudPassport);
        Environment testEnv = environmentRepository.findByNameAndCluster("env-3-namespaces", CLUSTER_NAME);
        assertThat(testEnv.getNamespaces(), hasSize(2));
        clusterResourcesLoader.loadClusterResources(coreV1Api, cloudPassport);
        assertThat(testEnv.getNamespaces(), hasSize(2));
        assertThat(testEnv.getNamespaces(), hasItems(hasProperty("name", equalTo(NAMESPACE_NAME)), hasProperty("name", equalTo(NAMESPACE_NAME_2))));
    }

    @Test
    void load_env_with_infrastructure_type() throws ApiException {
        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME), Map.of(LABEL_DISCOVERY_CLI_IO_LEVEL, LABEL_LEVEL_INFRA));

        clusterResourcesLoader.loadClusterResources(coreV1Api, CLOUD_PASSPORT);
        Environment testEnv = environmentRepository.findByNameAndCluster(ENV_1, CLUSTER_NAME);
        assertThat(testEnv.getType(), equalTo(EnvironmentType.INFRASTRUCTURE));
    }

    @Test
    void load_env_with_cse_toolset_type() throws ApiException {
        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME),
                Map.of(LABEL_DISCOVERY_CLI_IO_LEVEL, LABEL_LEVEL_APPS,
                        LABEL_DISCOVERY_CLI_IO_TYPE, LABEL_TYPE_CSE_TOOLSET));

        clusterResourcesLoader.loadClusterResources(coreV1Api, CLOUD_PASSPORT);
        Environment testEnv = environmentRepository.findByNameAndCluster(ENV_1, CLUSTER_NAME);
        assertThat(testEnv.getType(), equalTo(EnvironmentType.CSE_TOOLSET));
    }

    @Test
    void type_should_not_be_changed_if_it_was_manually_set() throws ApiException {
        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME),
                Map.of(LABEL_DISCOVERY_CLI_IO_LEVEL, LABEL_LEVEL_APPS,
                        LABEL_DISCOVERY_CLI_IO_TYPE, LABEL_TYPE_CSE_TOOLSET));

        clusterResourcesLoader.loadClusterResources(coreV1Api, CLOUD_PASSPORT);

        Environment testEnv = environmentRepository.findByNameAndCluster(ENV_1, CLUSTER_NAME);
        assertThat(testEnv.getType(), equalTo(EnvironmentType.CSE_TOOLSET));
        testEnv.setType(EnvironmentType.DESIGN_TIME);
        environmentRepository.persist(testEnv);

        clusterResourcesLoader.loadClusterResources(coreV1Api, CLOUD_PASSPORT);

        assertThat(testEnv.getType(), equalTo(EnvironmentType.DESIGN_TIME));
    }

    @Test
    void load_resources_for_env_after_update() throws ApiException {
        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME));

        V1ConfigMap configMap = new V1ConfigMap()
                .metadata(new V1ObjectMeta().name("sd-versions").uid("configmap-uid").creationTimestamp(DATE_2024))
                .data(Map.of("solution-descriptors-summary", "MyVersion 1.0.0"));
        mockConfigMaps(List.of(configMap), NAMESPACE_NAME);

        clusterResourcesLoader.loadClusterResources(coreV1Api, CLOUD_PASSPORT);
        Environment testEnv = environmentRepository.findByNameAndCluster(ENV_1, CLUSTER_NAME);
        assertThat(testEnv.getDeploymentVersion(), equalTo("MyVersion 1.0.0\n"));
        assertThat(testEnv.getCleanInstallationDate(), equalTo(DATE_2024.toInstant()));

        configMap = new V1ConfigMap()
                .metadata(new V1ObjectMeta().name("sd-versions").uid("configmap-uid").creationTimestamp(DATE_2025))
                .data(Map.of("solution-descriptors-summary", "MyVersion 2.0.0"));
        mockConfigMaps(List.of(configMap), NAMESPACE_NAME);

        clusterResourcesLoader.loadClusterResources(coreV1Api, CLOUD_PASSPORT);
        assertThat(testEnv.getDeploymentVersion(), equalTo("MyVersion 2.0.0\n"));
        assertThat(testEnv.getCleanInstallationDate(), equalTo(DATE_2025.toInstant()));
    }

    @Test
    void combine_deployment_version_for_namespaces() throws ApiException {
        CloudPassport cloudPassport = new CloudPassport(CLUSTER_NAME, "42", "https://api.example.com",
                Set.of(new CloudPassportEnvironment("env-3-namespaces", "some env for tests",
                        List.of(new CloudPassportNamespace(NAMESPACE_NAME),
                                new CloudPassportNamespace(NAMESPACE_NAME_2)))), null);
        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME, NAMESPACE_NAME_2));

        V1ConfigMap configMap1 = new V1ConfigMap()
                .metadata(new V1ObjectMeta().name("sd-versions").uid("configmap-uid").creationTimestamp(DATE_2025))
                .data(Map.of("solution-descriptors-summary", "MyVersion 1.0.0"));

        mockConfigMaps(List.of(configMap1), NAMESPACE_NAME);

        V1ConfigMap configMap2 = new V1ConfigMap()
                .metadata(new V1ObjectMeta().name("sd-versions").uid("configmap-uid").creationTimestamp(DATE_2024))
                .data(Map.of("solution-descriptors-summary", "MyVersion 2.0.0"));

        mockConfigMaps(List.of(configMap2), NAMESPACE_NAME_2);

        clusterResourcesLoader.loadClusterResources(coreV1Api, cloudPassport);
        Environment testEnv = environmentRepository.findByNameAndCluster("env-3-namespaces", CLUSTER_NAME);
        assertThat(testEnv.getDeploymentVersion(), equalTo("MyVersion 1.0.0\nMyVersion 2.0.0\n"));
        assertThat(testEnv.getCleanInstallationDate(), equalTo(DATE_2025.toInstant()));
    }

    @Test
    void try_to_load_namespace_from_cloud_passport_that_does_not_exist_in_k8s() throws ApiException {
        CloudPassport cloudPassport = new CloudPassport(CLUSTER_NAME, "42", "https://api.example.com",
                Set.of(new CloudPassportEnvironment("env-2-namespaces", "some env for tests",
                        List.of(new CloudPassportNamespace(NAMESPACE_NAME),
                                new CloudPassportNamespace("non-existing-namespace")))), null);
        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME));

        clusterResourcesLoader.loadClusterResources(coreV1Api, cloudPassport);
        Environment testEnv = environmentRepository.findByNameAndCluster("env-2-namespaces", CLUSTER_NAME);
        assertThat(testEnv.getNamespaces(), hasSize(2));
        assertThat(testEnv.getNamespaces(), hasItems(
                allOf(hasProperty("name", equalTo(NAMESPACE_NAME)), hasProperty("existsInK8s", equalTo(true))),
                allOf(hasProperty("name", equalTo("non-existing-namespace")), hasProperty("existsInK8s", equalTo(false)))));
    }

    @Test
    void load_resources_from_unreachable_cluster() throws ApiException {
        CloudPassport cloudPassport = new CloudPassport("unreachable-cluster", "42", "https://some.unreachable.url",
                Set.of(new CloudPassportEnvironment("env-unreachable", "some env for tests",
                        List.of(new CloudPassportNamespace(NAMESPACE_NAME)))), URI.create("http://localhost:" + port));

        CoreV1Api.APIlistNamespaceRequest nsRequest = mock(CoreV1Api.APIlistNamespaceRequest.class);
        when(coreV1Api.listNamespace()).thenReturn(nsRequest);
        when(nsRequest.execute()).thenThrow(new ApiException());

        clusterResourcesLoader.loadClusterResources(coreV1Api, cloudPassport);
        Environment testEnv = environmentRepository.findByNameAndCluster("env-unreachable", "unreachable-cluster");
        assertThat(testEnv, hasProperty("name", equalTo("env-unreachable")));
        assertThat(testEnv.getNamespaces(), hasSize(1));
        assertThat(testEnv.getNamespaces(), hasItems(allOf(
                hasProperty("existsInK8s", equalTo(false)),
                hasProperty("name", equalTo(NAMESPACE_NAME)))));
        assertThat(testEnv.getCluster(), allOf(
                hasProperty("name", equalTo("unreachable-cluster")),
                hasProperty("synced", equalTo(false))));
    }

    @Test
    void load_namespace_that_created_after_first_loading() throws ApiException {
        CloudPassport cloudPassport = new CloudPassport(CLUSTER_NAME, "42", "https://api.example.com",
                Set.of(new CloudPassportEnvironment("env-with-new-namespace", "some env for tests",
                        List.of(new CloudPassportNamespace(NAMESPACE_NAME), new CloudPassportNamespace("new-namespace")))), null);
        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME));

        clusterResourcesLoader.loadClusterResources(coreV1Api, cloudPassport);
        Environment testEnv = environmentRepository.findByNameAndCluster("env-with-new-namespace", CLUSTER_NAME);
        assertThat(testEnv.getNamespaces(), hasSize(2));

        assertThat(testEnv.getNamespaces(), hasItems(
                allOf(hasProperty("name", equalTo(NAMESPACE_NAME)), hasProperty("existsInK8s", equalTo(true))),
                allOf(hasProperty("name", equalTo("new-namespace")), hasProperty("existsInK8s", equalTo(false)))));

        mockNamespaceLoading(CLUSTER_NAME, List.of(NAMESPACE_NAME, "new-namespace"));
        clusterResourcesLoader.loadClusterResources(coreV1Api, cloudPassport);
        Environment testEnv2 = environmentRepository.findByNameAndCluster("env-with-new-namespace", CLUSTER_NAME);

        assertThat(testEnv2.getNamespaces(), hasSize(2));

        assertThat(testEnv2.getNamespaces(), hasItems(
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
        mockNamespaceLoading(clusterName, namespaceNames,
                Map.of(LABEL_DISCOVERY_CLI_IO_LEVEL, LABEL_LEVEL_APPS,
                        LABEL_DISCOVERY_CLI_IO_TYPE, LABEL_TYPE_CORE));
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

}
