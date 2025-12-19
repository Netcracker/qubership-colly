package org.qubership.colly.db.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.qubership.colly.db.data.Cluster;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class ClusterRepository {

    private static final String CLUSTER_KEY_PREFIX = "operations:cluster:";
    private static final String CLUSTER_NAME_INDEX_PREFIX = "inventory:idx:clusters:by-name:";
    @Inject
    RedisDataSource redisDataSource;
    @Inject
    ObjectMapper objectMapper;

    private HashCommands<String, String, String> hashCommands() {
        return redisDataSource.hash(String.class, String.class, String.class);
    }

    private KeyCommands<String> keyCommands() {
        return redisDataSource.key(String.class);
    }

    private ValueCommands<String, String> valueCommands() {
        return redisDataSource.value(String.class, String.class);
    }


    public Cluster save(Cluster cluster) {
        try {
            String key = CLUSTER_KEY_PREFIX + cluster.getId();
            String json = objectMapper.writeValueAsString(cluster);
            hashCommands().hset(key, "data", json);

            String nameIndexKey = CLUSTER_NAME_INDEX_PREFIX + cluster.getName();
            valueCommands().set(nameIndexKey, cluster.getId());
            return cluster;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize cluster", e);
        }
    }

    public Cluster findByName(String name) {
        try {
            String nameIndexKey = CLUSTER_NAME_INDEX_PREFIX + name;
            String clusterId = valueCommands().get(nameIndexKey);
            if (clusterId == null) {
                return null;
            }
            return findById(clusterId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to find cluster by name: " + name, e);
        }
    }

    public List<Cluster> findAll() {
        try {
            List<String> keys = keyCommands().keys(CLUSTER_KEY_PREFIX + "*");
            return keys.stream()
                    .map(key -> hashCommands().hget(key, "data"))
                    .filter(json -> json != null)
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

    public Cluster findById(String id) {
        try {
            String key = CLUSTER_KEY_PREFIX + id;
            String json = hashCommands().hget(key, "data");
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, Cluster.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize cluster", e);
        }
    }
}
