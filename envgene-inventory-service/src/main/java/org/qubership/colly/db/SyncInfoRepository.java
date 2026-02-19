package org.qubership.colly.db;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
public class SyncInfoRepository {

    private static final String SYNC_INFO_KEY = "inventory:sync-info";

    @Inject
    RedisDataSource redisDataSource;

    private HashCommands<String, String, String> hashCommands() {
        return redisDataSource.hash(String.class, String.class, String.class);
    }

    public void saveLastProjectSyncAt(Instant instant) {
        hashCommands().hset(SYNC_INFO_KEY, "lastProjectSyncAt", instant.toString());
    }

    public Instant getLastProjectSyncAt() {
        String value = hashCommands().hget(SYNC_INFO_KEY, "lastProjectSyncAt");
        if (value == null) {
            return null;
        }
        return Instant.parse(value);
    }
}
