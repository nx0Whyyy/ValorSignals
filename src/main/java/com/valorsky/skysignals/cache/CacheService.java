package com.valorsky.skysignals.cache;

import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.model.EventState;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class CacheService {

    private final Cache<String, EventState> cache;
    private final Config config;

    public CacheService(Config config) {
        this.config = config;
        this.cache = Caffeine.newBuilder()
                .maximumSize(config.cacheMaximumSize())
                .expireAfterWrite(config.cacheExpireAfterMinutes(), TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    public void put(String key, EventState state) {
        cache.put(key, state);
    }

    public EventState get(String key) {
        return cache.getIfPresent(key);
    }

    public EventState getIfPresent(String key) {
        return cache.getIfPresent(key);
    }

    public void remove(String key) {
        cache.invalidate(key);
    }

    public void removeAll() {
        cache.invalidateAll();
    }

    public List<EventState> getAll() {
        return cache.asMap().values().stream().toList();
    }

    public List<EventState> getActiveEvents(String serverId) {
        return cache.asMap().values().stream()
                .filter(state -> state.status() != com.valorsky.skysignals.model.SkyEventStatus.FINISHED
                        && state.status() != com.valorsky.skysignals.model.SkyEventStatus.CANCELLED)
                .filter(state -> serverId == null || state.serverId().equals(serverId))
                .toList();
    }

    public void clear() {
        cache.invalidateAll();
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    public long size() {
        return cache.estimatedSize();
    }

    public double hitRate() {
        CacheStats stats = cache.stats();
        long hits = stats.hitCount();
        long misses = stats.missCount();
        long total = hits + misses;
        return total > 0 ? (double) hits / total : 0.0;
    }

    public Map<String, Object> getStats() {
        CacheStats stats = cache.stats();
        return Map.of(
                "size", cache.estimatedSize(),
                "hitCount", stats.hitCount(),
                "missCount", stats.missCount(),
                "hitRate", hitRate()
        );
    }
}
