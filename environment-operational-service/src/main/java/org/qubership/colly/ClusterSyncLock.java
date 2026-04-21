package org.qubership.colly;

import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis-based distributed lock for per-cluster sync operations.
 * Prevents concurrent sync of the same cluster across threads and service instances.
 * <p>
 * Lock key: lock:sync:cluster:{clusterId}
 * Implementation: SETNX + EXPIRE (non-atomic but acceptable — TTL >> max sync duration).
 */
@ApplicationScoped
public class ClusterSyncLock {

    private static final String LOCK_KEY_PREFIX = "lock:sync:cluster:";

    // Stores ownerId per clusterId to safely release only our own locks
    private final ConcurrentHashMap<String, String> activeOwnerIds = new ConcurrentHashMap<>();

    @Inject
    RedisDataSource redisDataSource;

    private ValueCommands<String, String> valueCommands() {
        return redisDataSource.value(String.class, String.class);
    }

    private KeyCommands<String> keyCommands() {
        return redisDataSource.key(String.class);
    }

    /**
     * Atomically acquires a lock for the given cluster.
     * Uses SETNX to prevent concurrent acquisition, then sets TTL to prevent eternal lock on crash.
     *
     * @param clusterId cluster identifier
     * @param ttl       lock expiration (should be > max expected sync duration)
     * @return true if lock acquired, false if already held by another thread/instance
     */
    public boolean tryAcquire(String clusterId, Duration ttl) {
        String key = LOCK_KEY_PREFIX + clusterId;
        String ownerId = UUID.randomUUID().toString();
        boolean acquired = valueCommands().setnx(key, ownerId);
        if (acquired) {
            keyCommands().expire(key, ttl);
            activeOwnerIds.put(clusterId, ownerId);
            Log.debugf("Lock acquired for cluster %s (owner=%s)", clusterId, ownerId);
        } else {
            Log.debugf("Lock not acquired for cluster %s — already held", clusterId);
        }
        return acquired;
    }

    /**
     * Releases the lock for the given cluster.
     * Only deletes the Redis key if we still own it (guards against TTL expiry + re-acquisition).
     * Safe to call even if tryAcquire returned false or lock already expired.
     *
     * @param clusterId cluster identifier
     */
    public void release(String clusterId) {
        String ownerId = activeOwnerIds.remove(clusterId);
        if (ownerId == null) {
            return;
        }
        String key = LOCK_KEY_PREFIX + clusterId;
        String current = valueCommands().get(key);
        if (ownerId.equals(current)) {
            keyCommands().del(key);
            Log.debugf("Lock released for cluster %s", clusterId);
        } else {
            Log.warnf("Lock for cluster %s was already expired or taken by another owner — skipping DEL", clusterId);
        }
    }
}
