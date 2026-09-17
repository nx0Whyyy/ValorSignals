package com.valorsky.skysignals.api;

import com.valorsky.skysignals.cache.CacheService;
import com.valorsky.skysignals.database.DatabaseManager;
import com.valorsky.skysignals.database.EventRepository;
import com.valorsky.skysignals.event.SkyEventManager;
import com.valorsky.skysignals.event.SkyEventFactory;
import com.valorsky.skysignals.model.EventState;
import com.valorsky.skysignals.model.SkyEvent;
import com.valorsky.skysignals.model.SkyEventType;
import com.valorsky.skysignals.rabbitmq.RabbitManager;
import com.valorsky.skysignals.redis.RedisService;

import java.util.List;
import java.util.UUID;

public final class SkySignalsAPI {

    private static volatile SkySignalsAPI instance;

    private final SkyEventManager eventManager;
    private final CacheService cacheService;
    private final RedisService redisService;
    private final RabbitManager rabbitManager;
    private final DatabaseManager databaseManager;
    private final SkyEventFactory factory;

    public SkySignalsAPI(
            SkyEventManager eventManager,
            CacheService cacheService,
            RedisService redisService,
            RabbitManager rabbitManager,
            DatabaseManager databaseManager,
            SkyEventFactory factory
    ) {
        this.eventManager = eventManager;
        this.cacheService = cacheService;
        this.redisService = redisService;
        this.rabbitManager = rabbitManager;
        this.databaseManager = databaseManager;
        this.factory = factory;
        instance = this;
    }

    public static SkySignalsAPI getInstance() {
        return instance;
    }

    public SkyEventManager getEventManager() {
        return eventManager;
    }

    public SkyEventFactory getFactory() {
        return factory;
    }

    public CacheService getCacheService() {
        return cacheService;
    }

    public RedisService getRedisService() {
        return redisService;
    }

    public RabbitManager getRabbitManager() {
        return rabbitManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public boolean getDatabaseConnected() {
        return databaseManager != null && databaseManager.isConnected();
    }

    public List<SkyEvent> getActiveEvents() {
        return eventManager.getActiveEvents();
    }

    public SkyEvent getActiveEvent() {
        return eventManager.getActiveEvent();
    }

    public SkyEvent getActiveEvent(SkyEventType type) {
        return eventManager.getActiveEvents().stream()
                .filter(e -> e.getType() == type)
                .findFirst()
                .orElse(null);
    }

    public boolean isEventActive() {
        return eventManager.isEventActive();
    }

    public boolean isEventTypeActive(SkyEventType type) {
        return eventManager.isEventTypeActive(type);
    }

    public List<EventRepository.EventHistoryEntry> getRecentEvents(int limit) {
        EventRepository repo = eventManager.getEventRepository();
        if (repo == null) return List.of();
        return repo.getRecentEvents(limit);
    }

    public void reload() {
        eventManager.reload();
    }

    public SkyEvent getEvent(UUID id) {
        return eventManager.getActiveEvents().stream()
                .filter(e -> e.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}
