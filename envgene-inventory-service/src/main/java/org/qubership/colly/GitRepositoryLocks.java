package org.qubership.colly;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Serializes access to a local git working copy: writes (sync + modify + commit + push)
 * and the scheduled re-clone that replaces the folder must never overlap.
 */
@ApplicationScoped
public class GitRepositoryLocks {

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T withLock(File repositoryPath, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(key(repositoryPath), k -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    public void withLock(File repositoryPath, Runnable action) {
        withLock(repositoryPath, () -> {
            action.run();
            return null;
        });
    }

    private static String key(File repositoryPath) {
        return repositoryPath.toPath().toAbsolutePath().normalize().toString();
    }
}
