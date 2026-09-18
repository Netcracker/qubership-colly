package org.qubership.colly.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.set.SetCommands;
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
    private static final String CLUSTER_NAME_INDEX_PREFIX = "inventory:idx:clusters:by-name:";
    private static final String CLUSTER_PROJECT_ID_INDEX_PREFIX = "inventory:idx:clusters:by-project:";
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

    private SetCommands<String, String> setCommands() {
        return redisDataSource.set(String.class, String.class);
    }

    public void persist(Cluster cluster) {
        if (cluster.getId() == null) {
            cluster.setId(UUID.randomUUID().toString());
        }

        try {
            String newProjectId = cluster.getGitInfo().projectId();

            // Self-heal stale project membership: this id can end up assigned to a different
            // project than before (e.g. adopted from the legacy global name index by a project
            // other than the one it was last persisted under). Without this, the id would remain
            // a member of the OLD project's Set forever, so both projects' cluster lists would
            // show it.
            Cluster previous = findById(cluster.getId());
            if (previous != null && previous.getGitInfo() != null) {
                String previousProjectId = previous.getGitInfo().projectId();
                if (previousProjectId != null && !previousProjectId.equals(newProjectId)) {
                    setCommands().srem(CLUSTER_PROJECT_ID_INDEX_PREFIX + previousProjectId, cluster.getId());
                }
            }

            String key = CLUSTER_KEY_PREFIX + cluster.getId();
            String json = objectMapper.writeValueAsString(cluster);
            hashCommands().hset(key, "data", json);

            // Create name index for fast lookup, scoped by project - cluster names are only
            // unique within a project, not globally.
            String nameIndexKey = CLUSTER_NAME_INDEX_PREFIX + newProjectId + ":" + cluster.getName();
            valueCommands().set(nameIndexKey, cluster.getId());

            String projectIndexKey = CLUSTER_PROJECT_ID_INDEX_PREFIX + newProjectId;
            setCommands().sadd(projectIndexKey, cluster.getId());
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
            Log.info("Found " + keys.size() + " cluster keys: " + keys);
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

    public Cluster findByProjectIdAndName(String projectId, String name) {
        try {
            String nameIndexKey = CLUSTER_NAME_INDEX_PREFIX + projectId + ":" + name;
            String clusterId = valueCommands().get(nameIndexKey);
            if (clusterId == null) {
                return null;
            }
            return findById(clusterId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to find cluster by project id and name: " + projectId + "/" + name, e);
        }
    }

    /**
     * Legacy global (non-project-scoped) name lookup, kept only as a one-time adoption path for
     * clusters persisted before the by-project name index existed. Uses GETDEL so the legacy
     * entry is consumed atomically on first use: without this, the same stale entry could be
     * "adopted" a second time by a different project's cluster of the same name (re-introducing
     * the exact cross-project collision this index scoping fixes), and the orphaned key would
     * otherwise never be cleaned up. Once every cluster has been re-persisted with a scoped index
     * entry, this becomes dead code and can be removed.
     */
    public Cluster findByNameLegacy(String name) {
        try {
            String legacyNameIndexKey = "inventory:idx:clusters:by-name:" + name;
            String clusterId = valueCommands().getdel(legacyNameIndexKey);
            if (clusterId == null) {
                return null;
            }
            return findById(clusterId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to find cluster by legacy name index: " + name, e);
        }
    }

    public List<Cluster> findByProjectId(String projectId) {
        try {
            String projectIndexKey = CLUSTER_PROJECT_ID_INDEX_PREFIX + projectId;
            return setCommands().smembers(projectIndexKey).stream()
                    .map(this::findById)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to find clusters by project id: " + projectId, e);
        }
    }

    public void deleteById(String id) {
        Cluster cluster = findById(id);
        if (cluster == null) {
            return;
        }
        keyCommands().del(CLUSTER_KEY_PREFIX + id);
        if (cluster.getGitInfo() != null && cluster.getGitInfo().projectId() != null) {
            keyCommands().del(CLUSTER_NAME_INDEX_PREFIX + cluster.getGitInfo().projectId() + ":" + cluster.getName());
            String projectIndexKey = CLUSTER_PROJECT_ID_INDEX_PREFIX + cluster.getGitInfo().projectId();
            setCommands().srem(projectIndexKey, id);
        }
    }
}
