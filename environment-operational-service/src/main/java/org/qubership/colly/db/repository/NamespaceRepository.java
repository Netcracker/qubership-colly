package org.qubership.colly.db.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.qubership.colly.db.data.Namespace;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class NamespaceRepository {

    @Inject
    RedisDataSource redisDataSource;

    @Inject
    ObjectMapper objectMapper;

    private static final String NAMESPACE_KEY_PREFIX = "operations:namespace:";

    private HashCommands<String, String, String> hashCommands() {
        return redisDataSource.hash(String.class, String.class, String.class);
    }

    private KeyCommands<String> keyCommands() {
        return redisDataSource.key(String.class);
    }

    public Namespace save(Namespace namespace) {
        try {
            String key = NAMESPACE_KEY_PREFIX + namespace.getUid();
            String json = objectMapper.writeValueAsString(namespace);
            hashCommands().hset(key, "data", json);
            return namespace;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize namespace", e);
        }
    }

    public Optional<Namespace> findByUid(String uid) {
        try {
            String key = NAMESPACE_KEY_PREFIX + uid;
            String json = hashCommands().hget(key, "data");
            if (json == null) {
                return Optional.empty();
            }
            Namespace namespace = objectMapper.readValue(json, Namespace.class);
            return Optional.of(namespace);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize namespace", e);
        }
    }

    public List<Namespace> findAll() {
        try {
            List<String> keys = keyCommands().keys(NAMESPACE_KEY_PREFIX + "*");
            return keys.stream()
                    .map(key -> hashCommands().hget(key, "data"))
                    .filter(json -> json != null)
                    .map(json -> {
                        try {
                            return objectMapper.readValue(json, Namespace.class);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException("Failed to deserialize namespace", e);
                        }
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to find all namespaces", e);
        }
    }


    public List<Namespace> findByClusterId(String clusterId) {
        return findAll().stream()
                .filter(ns -> clusterId.equals(ns.getClusterId()))
                .collect(Collectors.toList());
    }

    public List<Namespace> findByEnvironmentId(String environmentId) {
        return findAll().stream()
                .filter(ns -> environmentId.equals(ns.getEnvironmentId()))
                .collect(Collectors.toList());
    }
}
