package com.valorsky.skysignals.redis;

import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.util.FoliaScheduler;
import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class DistributedLockService {

    private final JavaPlugin plugin;
    private final Config config;
    private final RedisClient client;
    private volatile RedisCommands<String, String> sync;
    private final Logger logger;
    private final String lockPrefix = "lock:skysignals:";
    private final boolean enabled;

    public DistributedLockService(JavaPlugin plugin, Config config, RedisClient client, Logger logger) {
        this.plugin = plugin;
        this.config = config;
        this.logger = logger;
        if (client == null) {
            this.client = null;
            this.sync = null;
            this.enabled = false;
            logger.warning("Distributed lock service initialized without Redis client. Locking disabled.");
            return;
        }
        this.client = client;
        boolean tmpEnabled = false;
        try {
            this.sync = client.connect().sync();
            tmpEnabled = true;
            logger.info("Distributed lock service initialized.");
        } catch (Exception e) {
            logger.warning("Failed to initialize distributed lock service: " + e.getMessage());
        }
        this.enabled = tmpEnabled;
    }

    private CompletableFuture<Boolean> runAsync(Supplier<Boolean> action) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        FoliaScheduler.runAsync(plugin, () -> {
            try {
                future.complete(action.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Boolean> tryLock(String lockName, Duration ttl) {
        String ownerId = config.serverId() + ":" + System.currentTimeMillis();
        return tryLock(lockName, ttl, ownerId);
    }

    public CompletableFuture<Boolean> tryLock(String lockName, Duration ttl, String ownerId) {
        return runAsync(() -> {
            if (!enabled || sync == null) {
                return false;
            }
            String key = lockPrefix + lockName;
            String value = ownerId + ":" + System.currentTimeMillis();
            String acquired = sync.set(key, value, new SetArgs().nx().ex((int) ttl.getSeconds()));
            return acquired != null;
        });
    }

    public CompletableFuture<Boolean> releaseLock(String lockName, String ownerId) {
        return runAsync(() -> {
            if (!enabled || sync == null) {
                return true;
            }
            String key = lockPrefix + lockName;
            String currentValue = sync.get(key);
            if (currentValue != null && currentValue.startsWith(ownerId + ":")) {
                sync.del(key);
                return true;
            }
            return false;
        });
    }

    public CompletableFuture<Boolean> extendLock(String lockName, Duration ttl, String ownerId) {
        return runAsync(() -> {
            if (!enabled || sync == null) {
                return false;
            }
            String key = lockPrefix + lockName;
            String currentValue = sync.get(key);
            if (currentValue != null && currentValue.startsWith(ownerId + ":")) {
                sync.expire(key, (int) ttl.getSeconds());
                return true;
            }
            return false;
        });
    }

    public boolean isLocked(String lockName) {
        if (!enabled || sync == null) {
            return false;
        }
        String key = lockPrefix + lockName;
        return sync.exists(key) > 0;
    }

    public String getLockOwner(String lockName) {
        if (!enabled || sync == null) {
            return null;
        }
        String key = lockPrefix + lockName;
        return sync.get(key);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void shutdown() {
    }

    public <T> CompletableFuture<T> executeWithLock(String lockName, Duration ttl, String ownerId, Supplier<T> action) {
        return tryLock(lockName, ttl, ownerId).thenCompose(acquired -> {
            if (!acquired && enabled) {
                return CompletableFuture.failedFuture(new IllegalStateException("Could not acquire lock: " + lockName));
            }
            try {
                T result = action.get();
                return CompletableFuture.completedFuture(result);
            } catch (Exception e) {
                return CompletableFuture.failedFuture(e);
            } finally {
                releaseLock(lockName, ownerId).join();
            }
        });
    }
}
