package org.qubership.colly.db.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.qubership.colly.db.data.Environment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class EnvironmentRepository {

    private static final String ENVIRONMENT_KEY_PREFIX = "operations:environment:";
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

    public Environment save(Environment environment) {
        if (environment.getId() == null) {
            environment.setId(UUID.randomUUID().toString());
        }

        try {
            String key = ENVIRONMENT_KEY_PREFIX + environment.getId();
            String json = objectMapper.writeValueAsString(environment);
            hashCommands().hset(key, "data", json);
            return environment;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize environment", e);
        }
    }

    public Optional<Environment> findById(String id) {
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

    public List<Environment> findAll() {
        try {
            List<String> keys = keyCommands().keys(ENVIRONMENT_KEY_PREFIX + "*");
            return keys.stream()
                    .map(key -> hashCommands().hget(key, "data"))
                    .filter(json -> json != null)
                    .map(json -> {
                        try {
                            return objectMapper.readValue(json, Environment.class);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException("Failed to deserialize environment", e);
                        }
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to find all environments", e);
        }
    }

    public void delete(String id) {
        String key = ENVIRONMENT_KEY_PREFIX + id;
        keyCommands().del(key);
    }

    public List<Environment> findByName(String name) {
        return findAll().stream()
                .filter(env -> name.equals(env.getName()))
                .collect(Collectors.toList());
    }
}
