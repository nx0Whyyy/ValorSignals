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
    private io.lettuce.core.api.StatefulRedisConnection<String, String> connection;
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
            this.connection = client.connect();
            this.sync = connection.sync();
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
            String value = ownerId;
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
            Long removed = sync.eval("if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
                    io.lettuce.core.ScriptOutputType.INTEGER, new String[]{key}, ownerId);
            return removed != null && removed == 1;

        });
    }

    public CompletableFuture<Boolean> extendLock(String lockName, Duration ttl, String ownerId) {
        return runAsync(() -> {
            if (!enabled || sync == null) {
                return false;
            }
            String key = lockPrefix + lockName;
            Long extended = sync.eval("if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('expire', KEYS[1], ARGV[2]) else return 0 end",
                    io.lettuce.core.ScriptOutputType.INTEGER, new String[]{key}, ownerId, Long.toString(Math.max(1, ttl.getSeconds())));
            return extended != null && extended == 1;

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
        if (connection != null) connection.close();
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
