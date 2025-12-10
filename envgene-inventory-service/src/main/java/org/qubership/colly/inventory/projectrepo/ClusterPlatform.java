package org.qubership.colly.inventory.projectrepo;

public enum ClusterPlatform {
    OCP,
    K8S;;

    public static ClusterPlatform fromString(String clusterPlatform) {
        if (clusterPlatform == null || clusterPlatform.isEmpty()) {
            throw new IllegalArgumentException("Cluster platform cannot be null or empty");
        }
        return ClusterPlatform.valueOf(clusterPlatform.toUpperCase());
    }

}
