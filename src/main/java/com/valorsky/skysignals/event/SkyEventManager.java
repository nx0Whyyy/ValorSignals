package com.valorsky.skysignals.event;

import com.valorsky.skysignals.cache.CacheService;
import com.valorsky.skysignals.cache.LocalEventCache;
import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.database.EventRepository;
import com.valorsky.skysignals.model.*;
import com.valorsky.skysignals.notification.NotificationService;
import com.valorsky.skysignals.rabbitmq.EventPublisher;
import com.valorsky.skysignals.redis.RedisService;
import com.valorsky.skysignals.util.FoliaScheduler;
import org.bukkit.plugin.java.JavaPlugin;

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
    private EventRepository eventRepository;
    private final NotificationService notificationService;
    private final Config config;
    private final Logger logger;
    private final EventResourceRegistry resourceRegistry;
    private EventContext eventContext;

    private final Map<UUID, SkyEvent> activeEvents = new ConcurrentHashMap<>();
    private final Map<UUID, SkyEventPhase> eventPhases = new ConcurrentHashMap<>();
    private FoliaScheduler.TaskHandle tickTask;
    private boolean initialized = false;

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
        this.resourceRegistry = new EventResourceRegistry(plugin);
    }

    public void setEventContext(EventContext context) {
        this.eventContext = context;
    }

    public void initialize() {
        restoreEventsFromRedis();
        startTickTask();
        initialized = true;
        logger.info("SkyEventManager initialized with " + activeEvents.size() + " event(s) restored.");
    }

    private void startTickTask() {
        tickTask = FoliaScheduler.runGlobalTimer(plugin, this::tick, 20L, 20L);
    }

    public void tick() {
        if (activeEvents.isEmpty()) return;

        Instant now = Instant.now();
        List<SkyEvent> toProcess = new ArrayList<>(activeEvents.values());

        for (SkyEvent event : toProcess) {
            try {
                if (event.getStatus() == SkyEventStatus.ACTIVE ||
                    event.getStatus() == SkyEventStatus.ANNOUNCING ||
                    event.getStatus() == SkyEventStatus.WARNING ||
                    event.getStatus() == SkyEventStatus.COMPLETING) {

                    long elapsed = now.getEpochSecond() - event.getStartedAt().getEpochSecond();
                    event.tick(elapsed);

                    if (event.isExpired()) {
                        FoliaScheduler.runGlobal(plugin, () -> finishEvent(event, true));
                    }
                }
            } catch (Exception e) {
                logger.warning("Error ticking event " + event.getId() + ": " + e.getMessage());
                FoliaScheduler.runGlobal(plugin, () -> finishEvent(event, false));
            }
        }
    }

    public CompletableFuture<SkyEvent> createEvent(SkyEventType type, EventContext context) {
        return createEvent(type, config.serverId(), context);
    }

    public CompletableFuture<SkyEvent> createEvent(SkyEventType type, String serverId, EventContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!factory.isRegistered(type)) {
                    throw new IllegalArgumentException("Event type not registered: " + type);
                }

                if (!canStartNewEvent()) {
                    throw new IllegalStateException("Cannot start new event: concurrent limit reached or concurrent events disabled");
                }

                int duration = config.getEventDuration(type);
                int warningDuration = config.getEventWarningDuration(type);
                Instant now = Instant.now();

                SkyEvent event = factory.create(type, serverId, Duration.ofSeconds(duration), context);

                EventState state = toState(event, SkyEventStatus.SCHEDULED, SkyEventPhase.SCHEDULED, 0);
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

        Instant now = Instant.now();
        EventState state = toState(event, SkyEventStatus.ANNOUNCING, SkyEventPhase.ANNOUNCING, 0);
        state = state.withStatus(SkyEventStatus.ANNOUNCING).withPhase(SkyEventPhase.ANNOUNCING);

        cache.put(state.id().toString(), state);
        localCache.put(event);
        if (redisService != null && redisService.isConnected()) {
            redisService.updateEventState(state);
        }
        eventRepository.updateEventStatus(state.id(), SkyEventStatus.ANNOUNCING, now);

        event.onPhaseChange(SkyEventPhase.ANNOUNCING);
        notificationService.notifyEventStart(event);
        eventPublisher.publish("event.started", state);

        logger.info("Event announcing: " + event.getType() + " [" + event.getId() + "]");
    }

    public CompletableFuture<Void> startEvent(SkyEventType type, EventContext context) {
        return createEvent(type, context)
            .thenCompose(event -> CompletableFuture.runAsync(() ->
                FoliaScheduler.runGlobal(plugin, () -> startEvent(event))
            ));
    }

    public void transitionPhase(SkyEvent event, SkyEventPhase newPhase) {
        eventPhases.put(event.getId(), newPhase);
        event.onPhaseChange(newPhase);

        Instant now = Instant.now();
        long elapsed = now.getEpochSecond() - event.getStartedAt().getEpochSecond();

        SkyEventStatus status = phaseToStatus(newPhase);
        EventState state = toState(event, status, newPhase, elapsed);

        cache.put(state.id().toString(), state);
        localCache.put(event);
        if (redisService != null && redisService.isConnected()) {
            redisService.updateEventState(state);
        }
        eventRepository.updateEventStatus(state.id(), status, now);

        notificationService.notifyEventPhase(event, newPhase.name().toLowerCase());
        eventPublisher.publish("event.updated", state);

        logger.info("Event " + event.getType() + " [" + event.getId() + "] phase: " + newPhase);
    }

    private SkyEventStatus phaseToStatus(SkyEventPhase phase) {
        return switch (phase) {
            case SCHEDULED -> SkyEventStatus.SCHEDULED;
            case ANNOUNCING -> SkyEventStatus.ANNOUNCING;
            case WARNING -> SkyEventStatus.WARNING;
            case ACTIVE -> SkyEventStatus.ACTIVE;
            case COMPLETING -> SkyEventStatus.COMPLETING;
            case FINISHED -> SkyEventStatus.FINISHED;
            case CANCELLED -> SkyEventStatus.CANCELLED;
        };
    }

    public void finishEvent(SkyEvent event, boolean natural) {
        if (event.getStatus() == SkyEventStatus.FINISHED || event.getStatus() == SkyEventStatus.CANCELLED) {
            return;
        }

        activeEvents.remove(event.getId());
        resourceRegistry.cleanup(event.getId());

        event.stop();

        Instant now = Instant.now();
        EventState state = toState(event, SkyEventStatus.FINISHED, SkyEventPhase.FINISHED,
            now.getEpochSecond() - event.getStartedAt().getEpochSecond());

        cache.put(state.id().toString(), state);
        localCache.remove(state.id().toString());
        if (redisService != null && redisService.isConnected()) {
            redisService.removeEventState(state.id().toString());
        }
        eventRepository.updateEventStatus(state.id(), SkyEventStatus.FINISHED, now);

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

        resourceRegistry.cleanup(eventId);
        event.cancel();
        event.stop();

        Instant now = Instant.now();
        EventState state = toState(event, SkyEventStatus.CANCELLED, SkyEventPhase.CANCELLED,
            now.getEpochSecond() - event.getStartedAt().getEpochSecond());

        cache.put(state.id().toString(), state);
        localCache.remove(state.id().toString());
        if (redisService != null && redisService.isConnected()) {
            redisService.removeEventState(state.id().toString());
        }
        eventRepository.updateEventStatus(state.id(), SkyEventStatus.CANCELLED, now);

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

    public EventResourceRegistry getResourceRegistry() {
        return resourceRegistry;
    }

    private boolean canStartNewEvent() {
        if (!config.eventsAllowConcurrent()) {
            return activeEvents.isEmpty();
        }
        return activeEvents.size() < config.eventsMaxActive();
    }

    public void handleEventCreated(EventState state) {
        FoliaScheduler.runGlobal(plugin, () -> {
            if (activeEvents.containsKey(state.id())) return;
            cache.put(state.id().toString(), state);
        });
    }

    public void handleEventStarted(EventState state) {
        FoliaScheduler.runGlobal(plugin, () -> {
            if (activeEvents.containsKey(state.id())) return;
             SkyEvent event = factory.createFromState(state, eventContext);
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
            resourceRegistry.cleanup(state.id());
            logger.info("Event finished (synchronized): " + state.type() + " [" + state.id() + "]");
        });
    }

    private EventState toState(SkyEvent event, SkyEventStatus status, SkyEventPhase phase, long elapsedSeconds) {
        return new EventState(
            event.getId(),
            event.getType(),
            event.getServerId(),
            event.getScope(),
            event.getScheduledAt(),
            event.getStartedAt(),
            event.getEndsAt(),
            status,
            phase,
            elapsedSeconds,
            event instanceof AbstractSkyEvent ae ? ae.getExtraData() : Map.of()
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
                if (state.serverId().equals(config.serverId()) &&
                    (state.status() == SkyEventStatus.ACTIVE ||
                     state.status() == SkyEventStatus.ANNOUNCING ||
                     state.status() == SkyEventStatus.WARNING)) {

                    if (state.endsAt().isBefore(Instant.now())) {
                        redisService.removeEventState(state.id().toString());
                        eventRepository.updateEventStatus(state.id(), SkyEventStatus.FINISHED, Instant.now());
                        logger.info("Expired event cleaned up: " + state.type() + " [" + state.id() + "]");
                    } else {
                        SkyEvent event = factory.createFromState(state, eventContext);
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
        resourceRegistry.cleanupAll();
        activeEvents.clear();
        eventPhases.clear();
        cache.clear();
        localCache.clear();
        initialized = false;
    }

    public void reload() {
        shutdown();
        restoreEventsFromRedis();
        startTickTask();
    }

    public Set<SkyEventType> getRegisteredEventTypes() {
        return factory.getRegisteredTypes();
    }

    public EventRepository getEventRepository() {
        return eventRepository;
    }

    public boolean isInitialized() {
        return initialized;
    }
}