package org.qubership.colly.inventory.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.qubership.colly.inventory.projectrepo.Project;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class ProjectRepository {

    private static final String PROJECT_KEY_PREFIX = "inventory:project:";

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

    public void persist(Project project) {
        try {
            String key = PROJECT_KEY_PREFIX + project.id();
            String json = objectMapper.writeValueAsString(project);
            hashCommands().hset(key, "data", json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize project: " + project, e);
        }
    }

    public Optional<Project> findByIdOptional(String id) {
        try {
            String key = PROJECT_KEY_PREFIX + id;
            String json = hashCommands().hget(key, "data");
            if (json == null) {
                return Optional.empty();
            }
            Project project = objectMapper.readValue(json, Project.class);
            return Optional.of(project);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize project", e);
        }
    }

    public Project findById(String id) {
        return findByIdOptional(id).orElse(null);
    }

    public List<Project> listAll() {
        try {
            List<String> keys = keyCommands().keys(PROJECT_KEY_PREFIX + "*");
            Log.info("Found " + keys.size() + " project keys: " + keys);
            return keys.stream()
                    .map(key -> hashCommands().hget(key, "data"))
                    .filter(Objects::nonNull)
                    .map(json -> {
                        try {
                            return objectMapper.readValue(json, Project.class);
                        } catch (JsonProcessingException e) {
                            throw new RuntimeException("Failed to deserialize project", e);
                        }
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to find all projects", e);
        }
    }
}
