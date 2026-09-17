package com.valorsky.skysignals.event;

import com.valorsky.skysignals.cache.CacheService;
import com.valorsky.skysignals.cache.LocalEventCache;
import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.database.EventRepository;
import com.valorsky.skysignals.model.EventState;
import com.valorsky.skysignals.model.SkyEvent;
import com.valorsky.skysignals.model.SkyEventStatus;
import com.valorsky.skysignals.model.SkyEventType;
import com.valorsky.skysignals.notification.NotificationService;
import com.valorsky.skysignals.rabbitmq.EventPublisher;
import com.valorsky.skysignals.redis.RedisService;
import org.bukkit.plugin.java.JavaPlugin;

import com.valorsky.skysignals.util.FoliaScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class SkyEventManager {

    private final JavaPlugin plugin;
    private final SkyEventFactory factory;
    private final CacheService cache;
    private final LocalEventCache localCache;
    private final RedisService redisService;
    private final EventPublisher eventPublisher;
    private final EventRepository eventRepository;
    private final NotificationService notificationService;
    private final Config config;
    private final Logger logger;

    private final Map<UUID, SkyEvent> activeEvents = new ConcurrentHashMap<>();
    private final Set<SkyEventType> registeredEventTypes = new HashSet<>();
    private FoliaScheduler.TaskHandle tickTask;

    public SkyEventManager(
            JavaPlugin plugin,
            SkyEventFactory factory,
            CacheService cache,
            LocalEventCache localCache,
            RedisService redisService,
            EventPublisher eventPublisher,
            EventRepository eventRepository,
            NotificationService notificationService,
            Config config
    ) {
        this.plugin = plugin;
        this.factory = factory;
        this.cache = cache;
        this.localCache = localCache;
        this.redisService = redisService;
        this.eventPublisher = eventPublisher;
        this.eventRepository = eventRepository;
        this.notificationService = notificationService;
        this.config = config;
        this.logger = plugin.getLogger();
    }

    public void initialize() {
        restoreEventsFromRedis();
        startTickTask();
        logger.info("SkyEventManager initialized with " + activeEvents.size() + " event(s) restored.");
    }

    private void startTickTask() {
        tickTask = FoliaScheduler.runGlobalTimer(plugin, this::tick, 20L, 20L);
    }

    public void tick() {
        if (activeEvents.isEmpty()) return;

        List<SkyEvent> toRemove = new ArrayList<>();
        for (SkyEvent event : activeEvents.values()) {
            try {
                if (event.getStatus() == SkyEventStatus.ACTIVE) {
                    if (event.isExpired()) {
                        finishEvent(event, true);
                    } else {
                        event.tick();
                    }
                }
            } catch (Exception e) {
                logger.warning("Error ticking event " + event.getId() + ": " + e.getMessage());
                toRemove.add(event);
            }
        }
    }

    public CompletableFuture<SkyEvent> createEvent(SkyEventType type) {
        return createEvent(type, config.serverId());
    }

    public CompletableFuture<SkyEvent> createEvent(SkyEventType type, String serverId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!factory.isRegistered(type)) {
                    throw new IllegalArgumentException("Event type not registered: " + type);
                }

                if (!canStartNewEvent()) {
                    throw new IllegalStateException("Cannot start new event: concurrent limit reached or concurrent events disabled");
                }

                int duration = config.getEventDuration(type);
                SkyEvent event = factory.create(type, serverId, Duration.ofSeconds(duration));

                EventState state = toState(event);
                cache.put(state.id().toString(), state);
                localCache.put(event);
                if (redisService != null && redisService.isConnected()) {
                    redisService.storeEventState(state);
                }
                eventRepository.saveEventState(state);

                eventPublisher.publish("event.created", state);

                logger.info("Event created: " + type + " [" + event.getId() + "]");
                return event;
            } catch (Exception e) {
                logger.severe("Failed to create event " + type + ": " + e.getMessage());
                throw e;
            }
        });
    }

    public void startEvent(SkyEvent event) {
        if (!activeEvents.containsKey(event.getId())) {
            activeEvents.put(event.getId(), event);
        }

         EventState state = toState(event).withStatus(SkyEventStatus.ACTIVE);
        state = new EventState(state.id(), state.type(), state.serverId(), Instant.now(), state.endsAt(), SkyEventStatus.ACTIVE, state.data());

        cache.put(state.id().toString(), state);
        localCache.put(event);
        redisService.updateEventState(state);
        eventRepository.updateEventStatus(state.id(), SkyEventStatus.ACTIVE, Instant.now());

        event.start();
        notificationService.notifyEventStart(event);
        eventPublisher.publish("event.started", state);

        logger.info("Event started: " + event.getType() + " [" + event.getId() + "]");
    }

    public CompletableFuture<Void> startEvent(SkyEventType type) {
        return CompletableFuture.runAsync(() -> {
            try {
        SkyEvent event = createEvent(type).join();
        FoliaScheduler.runGlobal(plugin, () -> startEvent(event));
            } catch (Exception e) {
                logger.warning("Failed to start event " + type + ": " + e.getMessage());
            }
        });
    }

    public void finishEvent(SkyEvent event, boolean natural) {
        if (event.getStatus() == SkyEventStatus.FINISHED || event.getStatus() == SkyEventStatus.CANCELLED) {
            return;
        }

        activeEvents.remove(event.getId());

        event.stop();

        EventState state = toState(event).withStatus(SkyEventStatus.FINISHED);
        cache.put(state.id().toString(), state);
        localCache.remove(state.id().toString());
        redisService.removeEventState(state.id().toString());
        eventRepository.updateEventStatus(state.id(), SkyEventStatus.FINISHED, Instant.now());

        notificationService.notifyEventEnd(event);
        eventPublisher.publish("event.finished", state);

        String suffix = natural ? "finished" : "cancelled";
        logger.info("Event " + suffix + ": " + event.getType() + " [" + event.getId() + "]");
    }

    public void cancelEvent(UUID eventId) {
        SkyEvent event = activeEvents.remove(eventId);
        if (event == null) {
            event = localCache.get(eventId.toString());
            if (event == null) return;
        }
        event.cancel();
        event.stop();

        EventState state = toState(event).withStatus(SkyEventStatus.CANCELLED);
        cache.put(state.id().toString(), state);
        localCache.remove(state.id().toString());
        if (redisService != null && redisService.isConnected()) {
            redisService.removeEventState(state.id().toString());
        }
        eventRepository.updateEventStatus(state.id(), SkyEventStatus.CANCELLED, Instant.now());

        notificationService.notifyEventEnd(event);
        eventPublisher.publish("event.cancelled", state);

        logger.info("Event cancelled: " + event.getType() + " [" + event.getId() + "]");
    }

    public List<SkyEvent> getActiveEvents() {
        return new ArrayList<>(activeEvents.values());
    }

    public SkyEvent getActiveEvent() {
        if (activeEvents.isEmpty()) return null;
        return activeEvents.values().iterator().next();
    }

    public boolean isEventActive() {
        return !activeEvents.isEmpty();
    }

    public boolean isEventTypeActive(SkyEventType type) {
        return activeEvents.values().stream().anyMatch(e -> e.getType() == type);
    }

    private boolean canStartNewEvent() {
        if (!config.eventsAllowConcurrent()) {
            return activeEvents.isEmpty();
        }
        return activeEvents.size() < config.eventsMaxActive();
    }

    public void handleEventStarted(EventState state) {
        FoliaScheduler.runGlobal(plugin, () -> {
            if (activeEvents.containsKey(state.id())) {
                return;
            }
            SkyEvent event = factory.createFromState(state);
            activeEvents.put(state.id(), event);
            localCache.put(event);
            cache.put(state.id().toString(), state);
            notificationService.notifyEventStart(event);
            logger.info("Event synchronized from network: " + state.type() + " [" + state.id() + "]");
        });
    }

    public void handleEventFinished(EventState state) {
        FoliaScheduler.runGlobal(plugin, () -> {
            SkyEvent event = activeEvents.remove(state.id());
            localCache.remove(state.id().toString());
            cache.put(state.id().toString(), state);
            if (event != null) {
                event.stop();
                notificationService.notifyEventEnd(event);
            }
            logger.info("Event finished (synchronized): " + state.type() + " [" + state.id() + "]");
        });
    }

    public void handleEventCreated(EventState state) {
        if (state.serverId().equals(config.serverId())) return;
         cache.put(state.id().toString(), state);
    }

    private EventState toState(SkyEvent event) {
        return new EventState(
                event.getId(),
                event.getType(),
                event.getServerId(),
                event.getStartedAt(),
                event.getEndsAt(),
                event.getStatus(),
                Map.of()
        );
    }

    private void restoreEventsFromRedis() {
        if (redisService == null || !redisService.isConnected()) {
            logger.warning("Redis not available, cannot restore events from Redis.");
            return;
        }
        try {
            List<EventState> states = redisService.getAllActiveEvents();
            for (EventState state : states) {
                if (state.serverId().equals(config.serverId()) && state.status() == SkyEventStatus.ACTIVE) {
                    if (state.endsAt().isBefore(Instant.now())) {
                        redisService.removeEventState(state.id().toString());
                        eventRepository.updateEventStatus(state.id(), SkyEventStatus.FINISHED, Instant.now());
                        logger.info("Expired event cleaned up: " + state.type() + " [" + state.id() + "]");
                    } else {
                        SkyEvent event = factory.createFromState(state);
                        event.start();
                         activeEvents.put(state.id(), event);
                         localCache.put(event);
                         cache.put(state.id().toString(), state);
                        logger.info("Restored event: " + state.type() + " [" + state.id() + "]");
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to restore events from Redis: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        for (SkyEvent event : new ArrayList<>(activeEvents.values())) {
            finishEvent(event, false);
        }
        activeEvents.clear();
        cache.clear();
        localCache.clear();
    }

    public void reload() {
        shutdown();
        restoreEventsFromRedis();
        startTickTask();
    }

    public java.util.Set<SkyEventType> getRegisteredEventTypes() {
        return factory.getRegisteredTypes();
    }

    public EventRepository getEventRepository() {
        return eventRepository;
    }
}
