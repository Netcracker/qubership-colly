package org.qubership.colly.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.qubership.colly.db.data.Cluster;
import org.qubership.colly.db.data.Environment;
import org.qubership.colly.db.data.Namespace;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class ClusterRepository {

    @Inject
    RedisDataSource redisDataSource;

    @Inject
    ObjectMapper objectMapper;

    private static final String CLUSTER_KEY_PREFIX = "inventory:cluster:";

    private HashCommands<String, String, String> hashCommands() {
        return redisDataSource.hash(String.class, String.class, String.class);
    }

    private KeyCommands<String> keyCommands() {
        return redisDataSource.key(String.class);
    }

    public void persist(Cluster cluster) {
        if (cluster.getId() == null) {
            cluster.setId(UUID.randomUUID().toString());
        }

        try {
            String key = CLUSTER_KEY_PREFIX + cluster.getId();
            String json = objectMapper.writeValueAsString(cluster);
            hashCommands().hset(key, "data", json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize cluster:" + cluster, e);
        }
    }

    public Optional<Cluster> findByIdOptional(String id) {
        try {
            String key = CLUSTER_KEY_PREFIX + id;
            String json = hashCommands().hget(key, "data");
            if (json == null) {
                return Optional.empty();
            }
            Cluster cluster = objectMapper.readValue(json, Cluster.class);
            return Optional.of(cluster);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize cluster", e);
        }
    }

    public Cluster findById(String id) {
        return findByIdOptional(id).orElse(null);
    }

    public List<Cluster> listAll() {
        try {
            List<String> keys = keyCommands().keys(CLUSTER_KEY_PREFIX + "*");
            return keys.stream()
                    .map(key -> hashCommands().hget(key, "data"))
                    .filter(Objects::nonNull)
                    .map(json -> {
                        try {
                            return objectMapper.readValue(json, Cluster.class);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException("Failed to deserialize cluster", e);
                        }
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to find all clusters", e);
        }
    }

    public void delete(Cluster cluster) {
        if (cluster.getId() != null) {
            String key = CLUSTER_KEY_PREFIX + cluster.getId();
            keyCommands().del(key);
        }
    }

    public void deleteById(String id) {
        String key = CLUSTER_KEY_PREFIX + id;
        keyCommands().del(key);
    }

    public Cluster findByName(String name) {
        return listAll().stream()
                .filter(cluster -> name.equals(cluster.getName()))
                .findFirst()
                .orElse(null);
    }

    public List<Environment> findAllEnvironments() {
        return listAll().stream()
                .flatMap(cluster -> cluster.getEnvironments().stream())
                .collect(Collectors.toList());
    }

    public Environment findEnvironmentByNameAndCluster(String environmentName, String clusterName) {
        Cluster cluster = findByName(clusterName);
        if (cluster == null) return null;

        return cluster.getEnvironments().stream()
                .filter(env -> environmentName.equals(env.getName()))
                .findFirst()
                .orElse(null);
    }

    public Namespace findNamespaceByName(String namespaceName, Environment environment) {
        return environment.getNamespaces().stream()
                .filter(ns -> namespaceName.equals(ns.getName()))
                .findFirst()
                .orElse(null);
    }
}
