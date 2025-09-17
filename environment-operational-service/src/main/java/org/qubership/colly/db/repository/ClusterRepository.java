package org.qubership.colly.db.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.qubership.colly.db.data.Cluster;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class ClusterRepository {

    @Inject
    RedisDataSource redisDataSource;

    @Inject
    ObjectMapper objectMapper;

    private static final String CLUSTER_KEY_PREFIX = "cluster:";

    private HashCommands<String, String, String> hashCommands() {
        return redisDataSource.hash(String.class, String.class, String.class);
    }

    private KeyCommands<String> keyCommands() {
        return redisDataSource.key(String.class);
    }

    public Cluster save(Cluster cluster) {
        try {
            String key = CLUSTER_KEY_PREFIX + cluster.getName();
            String json = objectMapper.writeValueAsString(cluster);
            hashCommands().hset(key, "data", json);
            return cluster;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize cluster", e);
        }
    }

    public Optional<Cluster> findByName(String name) {
        try {
            String key = CLUSTER_KEY_PREFIX + name;
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
}
