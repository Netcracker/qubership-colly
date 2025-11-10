package org.qubership.colly.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.qubership.colly.db.data.Cluster;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class ClusterRepository {

    private static final String CLUSTER_KEY_PREFIX = "inventory:cluster:";
    private static final String CLUSTER_NAME_INDEX_PREFIX = "inventory:cluster:name:";
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

    public void persist(Cluster cluster) {
        if (cluster.getId() == null) {
            cluster.setId(UUID.randomUUID().toString());
        }

        try {
            String key = CLUSTER_KEY_PREFIX + cluster.getId();
            String json = objectMapper.writeValueAsString(cluster);
            hashCommands().hset(key, "data", json);

            // Create name index for fast lookup
            String nameIndexKey = CLUSTER_NAME_INDEX_PREFIX + cluster.getName();
            valueCommands().set(nameIndexKey, cluster.getId());
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
                    .filter(key -> !key.startsWith(CLUSTER_NAME_INDEX_PREFIX)) // Skip name index keys
                    .map(key -> {
                        try {
                            return hashCommands().hget(key, "data");
                        } catch (Exception e) {
                            //todo
                            Log.error("Unexpected behavior. Need research" + e);// Skip keys that are not hash type (might be old data or indexes)
                            return null;
                        }
                    })
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

}
