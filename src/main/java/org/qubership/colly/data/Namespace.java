package org.qubership.colly.data;

import java.util.List;

public record Namespace(String name,
                        String uid,
                        String envName,
                        List<Deployment> deployments,
                        List<ConfigMap> configMaps,
                        List<Pod> pods) {
}
