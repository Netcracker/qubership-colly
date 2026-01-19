package org.qubership.colly.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.set.SetCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.qubership.colly.db.data.Environment;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class EnvironmentRepository {

    private static final String ENVIRONMENT_KEY_PREFIX = "inventory:environment:";
    private static final String CLUSTER_ENVIRONMENTS_INDEX_PREFIX = "inventory:idx:environments:by-cluster:";
    private static final String ALL_ENVIRONMENTS_SET = "inventory:idx:environments:all";

    @Inject
    RedisDataSource redisDataSource;

    @Inject
    ObjectMapper objectMapper;

    private HashCommands<String, String, String> hashCommands() {
        return redisDataSource.hash(String.class, String.class, String.class);
    }

    private SetCommands<String, String> setCommands() {
        return redisDataSource.set(String.class, String.class);
    }

    public void persist(Environment environment) {
        try {
            String key = ENVIRONMENT_KEY_PREFIX + environment.getId();
            String json = objectMapper.writeValueAsString(environment);

            // Store environment data
            hashCommands().hset(key, "data", json);

            // Add to global environments set
            setCommands().sadd(ALL_ENVIRONMENTS_SET, environment.getId());

            // Add to cluster-specific index if clusterId is present
            String clusterIndexKey = CLUSTER_ENVIRONMENTS_INDEX_PREFIX + environment.getClusterId();
            setCommands().sadd(clusterIndexKey, environment.getId());

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize environment: " + environment, e);
        }
    }

    public Optional<Environment> findByIdOptional(String id) {
        try {
            String key = ENVIRONMENT_KEY_PREFIX + id;
            String json = hashCommands().hget(key, "data");
            if (json == null) {
                return Optional.empty();
            }
            Environment environment = objectMapper.readValue(json, Environment.class);
            return Optional.of(environment);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize environment", e);
        }
    }

    public Environment findById(String id) {
        return findByIdOptional(id).orElse(null);
    }

    public List<Environment> listAll() {
        try {
            Set<String> environmentIds = setCommands().smembers(ALL_ENVIRONMENTS_SET);
            return environmentIds.stream()
                    .map(this::findById)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Environment::getName))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to find all environments", e);
        }
    }

    public List<Environment> findByClusterId(String clusterId) {
        if (clusterId == null) {
            return List.of();
        }
        try {
            String clusterIndexKey = CLUSTER_ENVIRONMENTS_INDEX_PREFIX + clusterId;
            Set<String> environmentIds = setCommands().smembers(clusterIndexKey);
            return environmentIds.stream()
                    .map(this::findById)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Environment::getName))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to find environments for cluster: " + clusterId, e);
        }
    }

    public Environment findByNameAndClusterId(String name, String clusterId) {
        if (clusterId == null) {
            return null;
        }
        return findByClusterId(clusterId).stream()
                .filter(env -> name.equals(env.getName()))
                .findFirst()
                .orElse(null);
    }

}
