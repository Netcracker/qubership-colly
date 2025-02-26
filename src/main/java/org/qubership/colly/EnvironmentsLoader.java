package org.qubership.colly;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.compress.utils.Lists;
import org.qubership.colly.data.Cluster;
import org.qubership.colly.data.Environment;
import org.qubership.colly.data.Namespace;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class EnvironmentsLoader {

    public static final String ENVIRONMENT_NAME = "environmentName";
    @Inject
    KubeConfigLoader kubeConfigLoader;


    public List<Environment> loadEnvironments() {
        List<Environment> result = new ArrayList<>();
        List<KubeConfig> kubeConfigs = kubeConfigLoader.loadKubeConfigs();
        kubeConfigs.forEach(kubeConfig -> result.addAll(loadClusterEnvironments(kubeConfig)));
        return result;
    }

    private List<Environment> loadClusterEnvironments(KubeConfig kubeConfig) {
        List<Environment> environments;

        Cluster cluster = new Cluster(kubeConfig.getCurrentContext(), Lists.newArrayList());
        try {
            ApiClient client = ClientBuilder.kubeconfig(kubeConfig).build();
            Configuration.setDefaultApiClient(client);
        } catch (IOException e) {
            throw new RuntimeException("Can't load kubeconfig - " + kubeConfig.getCurrentContext(), e);
        }
        CoreV1Api api = new CoreV1Api();
        CoreV1Api.APIlistNamespaceRequest apilistNamespaceRequest = api.listNamespace();
        try {
            V1NamespaceList list = apilistNamespaceRequest.execute();
            Map<String, List<Namespace>> namespaceToEnvName = list.getItems()
                    .stream()
                    .map(v1Namespace ->
                            new Namespace(v1Namespace.getMetadata().getName(),
                                    v1Namespace.getMetadata().getUid(),
                                    v1Namespace.getMetadata().getLabels().getOrDefault(ENVIRONMENT_NAME, v1Namespace.getMetadata().getName()),
                                    Lists.newArrayList(),
                                    Lists.newArrayList(),
                                    Lists.newArrayList()))
                    .collect(Collectors.groupingBy(Namespace::envName));

            environments = new ArrayList<>(namespaceToEnvName.entrySet().stream()
                    .map(stringListEntry -> new Environment(stringListEntry.getKey(), cluster, stringListEntry.getValue()))
                    .toList());
            Log.debug("Loaded " + environments.size() + " environments for cluster = " + cluster.name());

        } catch (ApiException e) {
            throw new RuntimeException("Can't load resources from cluster - " + cluster, e);
        }
        return environments;
    }
}
