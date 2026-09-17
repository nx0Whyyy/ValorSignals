package com.valorsky.skysignals.redis;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.logging.Logger;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;

public final class DistributedLockService {

    private final RedisClient client;
    private final RedisCommands<String, String> sync;
    private final Executor asyncExecutor;
    private final Logger logger;
    private final String lockPrefix = "lock:skysignals:";
    private final boolean enabled;

    public DistributedLockService(RedisClient client, Logger logger) {
        this.logger = logger;
        if (client == null) {
            this.client = null;
            this.sync = null;
            this.asyncExecutor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "SkySignals-Lock-Async");
                t.setDaemon(true);
                return t;
            });
            this.enabled = false;
            logger.warning("DistributedLockService created without Redis client. Locking disabled.");
            return;
        }
        this.client = client;
        RedisCommands<String, String> tmpSync = null;
        boolean tmpEnabled = false;
        try {
            tmpSync = client.connect().sync();
            tmpEnabled = true;
            logger.info("Distributed lock service initialized.");
        } catch (Exception e) {
            logger.warning("Failed to initialize distributed lock service: " + e.getMessage());
        }
        this.sync = tmpSync;
        this.enabled = tmpEnabled;
        this.asyncExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "SkySignals-Lock-Async");
            t.setDaemon(true);
            return t;
        });
    }

    public CompletableFuture<Boolean> tryLock(String lockName, Duration ttl) {
        return CompletableFuture.supplyAsync(() -> {
            if (!enabled || sync == null) {
                return false;
            }
            String key = lockPrefix + lockName;
            String value = System.currentTimeMillis() + ":" + Thread.currentThread().getId();
            String acquired = sync.set(key, value, new SetArgs().nx().ex((int) ttl.getSeconds()));
            return acquired != null;
        }, asyncExecutor);
    }

    public CompletableFuture<Boolean> tryLock(String lockName, Duration ttl, String ownerId) {
        return CompletableFuture.supplyAsync(() -> {
            if (!enabled || sync == null) {
                return false;
            }
            String key = lockPrefix + lockName;
            String value = ownerId + ":" + System.currentTimeMillis();
            String acquired = sync.set(key, value, new SetArgs().nx().ex((int) ttl.getSeconds()));
            return acquired != null;
        }, asyncExecutor);
    }

    public CompletableFuture<Boolean> releaseLock(String lockName, String ownerId) {
        return CompletableFuture.supplyAsync(() -> {
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
        }, asyncExecutor);
    }

    public CompletableFuture<Boolean> extendLock(String lockName, Duration ttl, String ownerId) {
        return CompletableFuture.supplyAsync(() -> {
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
        }, asyncExecutor);
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
        if (asyncExecutor instanceof java.util.concurrent.ExecutorService es) {
            es.shutdown();
        }
    }

    // Execute with lock pattern
    public <T> CompletableFuture<T> executeWithLock(String lockName, Duration ttl, String ownerId, Supplier<T> action) {
        return tryLock(lockName, ttl, ownerId).thenCompose(acquired -> {
            if (!acquired && enabled) {
                return CompletableFuture.failedFuture(new IllegalStateException("Could not acquire lock: " + lockName));
            }
            if (!enabled) {
                try {
                    T result = action.get();
                    return CompletableFuture.completedFuture(result);
                } catch (Exception e) {
                    return CompletableFuture.failedFuture(e);
                }
            }
            try {
                T result = action.get();
                return CompletableFuture.completedFuture(result);
            } finally {
                releaseLock(lockName, ownerId).join();
            }
        });
    }
}