package com.valorsky.skysignals.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.valorsky.skysignals.model.SkyEvent;

import java.util.concurrent.TimeUnit;

public final class LocalEventCache {

    private final Cache<String, SkyEvent> cache;

    public LocalEventCache(int maxSize, int expireMinutes) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireMinutes, TimeUnit.MINUTES)
                .build();
    }

    public void put(SkyEvent event) {
        if (event == null) return;
        cache.put(event.getId().toString(), event);
    }

    public void put(com.valorsky.skysignals.model.EventState state, SkyEvent event) {
        if (event == null) return;
        cache.put(state.id().toString(), event);
    }

    public SkyEvent get(String key) {
        return cache.getIfPresent(key);
    }

    public SkyEvent get(java.util.UUID id) {
        return get(id.toString());
    }

    public void remove(String key) {
        cache.invalidate(key);
    }

    public void remove(java.util.UUID id) {
        cache.invalidate(id.toString());
    }

    public void clear() {
        cache.invalidateAll();
    }

    public long size() {
        return cache.estimatedSize();
    }
}
