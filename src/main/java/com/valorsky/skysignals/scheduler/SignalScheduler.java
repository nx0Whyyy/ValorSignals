package com.valorsky.skysignals.scheduler;

import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.event.EventContext;
import com.valorsky.skysignals.event.SkyEventManager;
import com.valorsky.skysignals.model.SkyEventType;
import com.valorsky.skysignals.redis.DistributedLockService;
import com.valorsky.skysignals.util.FoliaScheduler;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class SignalScheduler {

    private final JavaPlugin plugin;
    private final Config config;
    private final SkyEventManager eventManager;
    private final DistributedLockService lockService;
    private final EventContext eventContext;
    private final Logger logger;
    private final Random random = new Random();
    private FoliaScheduler.TaskHandle pendingTask;
    private long generation;
    private final Map<SkyEventType, Instant> lastEventTime = new ConcurrentHashMap<>();
    private Instant lastGlobalEvent = Instant.now().minusSeconds(3600);
    private boolean running = false;

    public SignalScheduler(
            JavaPlugin plugin,
            Config config,
            SkyEventManager eventManager,
            DistributedLockService lockService,
            EventContext eventContext
    ) {
        this.plugin = plugin;
        this.config = config;
        this.eventManager = eventManager;
        this.lockService = lockService;
        this.eventContext = eventContext;
        this.logger = plugin.getLogger();
    }

    public void start() {
        if (!config.schedulerEnabled()) {
            logger.info("Scheduler disabled in configuration.");
            return;
        }
        if (running) return;
        running = true;
        scheduleNextEvent();
        logger.info("Signal scheduler started with weights and cooldowns.");
    }

    private void scheduleNextEvent() {
        if (!running || !config.schedulerEnabled()) return;

        int minInterval = config.schedulerMinInterval();
        int maxInterval = config.schedulerMaxInterval();
        int delay = minInterval + random.nextInt(maxInterval - minInterval + 1);

        if (pendingTask != null) pendingTask.cancel();
        pendingTask = FoliaScheduler.runGlobalDelayed(plugin, this::tryTriggerEvent, delay * 20L);
        logger.info("Next SkySignal check in " + delay + " seconds.");
    }

    private void tryTriggerEvent() {
        if (!running) return;

        // Check global cooldown
        int globalCooldown = config.globalCooldown();
        if (Instant.now().isBefore(lastGlobalEvent.plusSeconds(globalCooldown))) {
            logger.fine("Global cooldown active, skipping this cycle.");
            scheduleNextEvent();
            return;
        }

        // Get allowed and enabled events
        Set<SkyEventType> allowed = config.getAllowedSchedulerEvents();
        if (allowed.isEmpty()) {
            logger.warning("No events allowed in scheduler configuration.");
            scheduleNextEvent();
            return;
        }

        List<SkyEventType> candidates = allowed.stream()
                .filter(config::isEventEnabled)
                .filter(this::canStartEvent)
                .toList();

        if (candidates.isEmpty()) {
            logger.fine("No candidate events available (cooldowns/conflicts/limits).");
            scheduleNextEvent();
            return;
        }

        // Weighted random selection
        SkyEventType selected = selectWeightedEvent(candidates);
        if (selected == null) {
            scheduleNextEvent();
            return;
        }

        // Try to acquire distributed lock
        String lockName = "scheduler:" + selected.name().toLowerCase();
        String ownerId = config.serverId() + ":" + System.currentTimeMillis();

        long currentGeneration = generation;
        CompletableFuture<Boolean> lock = lockService.isEnabled()
                ? lockService.tryLock(lockName, java.time.Duration.ofSeconds(30), ownerId)
                : CompletableFuture.completedFuture(true);
        lock.thenCompose(acquired -> FoliaScheduler.supplyGlobal(plugin, () -> {
            if (!running || generation != currentGeneration || !acquired || !canStartEvent(selected)) return false;
            return true;
        })).thenCompose(allowedNow -> allowedNow
                ? eventManager.startEvent(selected, eventContext).thenApply(v -> true)
                : CompletableFuture.completedFuture(false))
            .whenComplete((started, error) -> {
                if (lockService.isEnabled()) lockService.releaseLock(lockName, ownerId);
                if (!plugin.isEnabled()) return;
                FoliaScheduler.runGlobal(plugin, () -> {
                    if (!running || generation != currentGeneration) return;
                    if (error != null) logger.warning("Unable to start event: " + error.getMessage());
                    if (Boolean.TRUE.equals(started)) {
                        lastGlobalEvent = Instant.now();
                        lastEventTime.put(selected, lastGlobalEvent);
                    }
                    scheduleNextEvent();
                });
            });
    }

    private boolean canStartEvent(SkyEventType type) {
        // Check event-specific cooldown
        Instant lastTime = lastEventTime.get(type);
        if (lastTime != null && Instant.now().isBefore(lastTime.plusSeconds(config.globalCooldown()))) {
            return false;
        }

        // Check max active events
        if (!config.eventsAllowConcurrent() && eventManager.isEventActive()) {
            return false;
        }

        if (eventManager.getActiveEvents().size() >= config.eventsMaxActive()) return false;

        // Check event conflicts
        Map<SkyEventType, Set<SkyEventType>> conflicts = config.getEventConflicts();
        Set<SkyEventType> conflicting = conflicts.get(type);
        if (conflicting != null && !conflicting.isEmpty()) {
            for (SkyEventType activeType : eventManager.getActiveEvents().stream()
                    .map(e -> e.getType())
                    .toList()) {
                if (conflicting.contains(activeType)) {
                    return false;
                }
            }
        }

        // Check if event type is already active
        if (eventManager.isEventTypeActive(type)) {
            return false;
        }

        return true;
    }

    private SkyEventType selectWeightedEvent(List<SkyEventType> candidates) {
        Map<SkyEventType, Integer> weights = config.getEventWeights();
        int totalWeight = candidates.stream()
                .mapToInt(t -> weights.getOrDefault(t, 10))
                .sum();

        if (totalWeight <= 0) {
            return candidates.get(random.nextInt(candidates.size()));
        }

        int r = random.nextInt(totalWeight);
        int cumulative = 0;

        for (SkyEventType type : candidates) {
            cumulative += weights.getOrDefault(type, 10);
            if (r < cumulative) {
                return type;
            }
        }

        return candidates.get(candidates.size() - 1);
    }

    public void stop() {
        running = false;
        generation++;
        if (pendingTask != null) pendingTask.cancel();
        pendingTask = null;
    }

    public void reload() {
        stop();
        lastEventTime.clear();
        lastGlobalEvent = Instant.now().minusSeconds(3600);
        if (config.schedulerEnabled()) {
            start();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public Map<SkyEventType, Instant> getLastEventTimes() {
        return Map.copyOf(lastEventTime);
    }
}