package org.qubership.colly.monitoring;

import io.quarkus.logging.Log;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithParentName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;

@ApplicationScoped
public class MonitoringService {

    @Inject
    MonitoringParams monitoringParams;

    public Map<String, String> loadMonitoringData(String monitoringUri, String environmentName, String clusterName, List<String> namespaceNames) {
        if (monitoringUri == null) {
            return emptyMap();
        }
        MonitoringClient monitoringClient;
        HashMap<String, String> result = new HashMap<>();
        try {
            monitoringClient = RestClientBuilder.newBuilder().baseUri(monitoringUri).build(MonitoringClient.class);

            Collection<MonitoringParam> monitoringParams = this.monitoringParams.allMonitoringParams().values();
            if (monitoringParams.isEmpty()) {
                return emptyMap();
            }

            for (MonitoringParam monitoringParam : monitoringParams) {

                String monitoringQuery = monitoringParam.query()
                        .replace("{namespace}", String.join("|", namespaceNames))
                        .replace("{env}", environmentName)
                        .replace("{cluster}", clusterName);

                Log.info("Executing query: " + monitoringQuery + " on " + monitoringUri + " for namespaces: " + namespaceNames);
                MonitoringResponse monitoringResponse = monitoringClient.executeQuery(monitoringQuery);
                if (monitoringResponse == null || monitoringResponse.data == null || monitoringResponse.data.result == null || monitoringResponse.data.result.isEmpty()) {
                    continue;
                }

                String monitoringData = monitoringResponse.data.result.getFirst().value.getLast();
                Log.info("Monitoring data for " + monitoringParam.name() + " is " + monitoringData);
                result.put(monitoringParam.name(), monitoringData);
            }
        } catch (Exception e) {
            Log.error("Unable to load monitoring data from " + monitoringUri, e);
            return emptyMap();
        }
        return result;
    }

    public List<String> getParameters() {
        List<String> paramNames = monitoringParams.allMonitoringParams()
                .values()
                .stream()
                .map(MonitoringParam::name)
                .sorted()
                .toList();
        Log.info("Found " + paramNames.size() + " monitoring parameters: " + paramNames);
        return paramNames;
    }

    @ConfigMapping(prefix = "colly.environment-operational-service.monitoring")
    public interface MonitoringParams {
        @WithParentName
        Map<String, MonitoringParam> allMonitoringParams();
    }


    public interface MonitoringParam {
        String name();

        String query();
    }
}
