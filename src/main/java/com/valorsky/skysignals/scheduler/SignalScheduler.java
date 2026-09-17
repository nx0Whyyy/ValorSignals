package com.valorsky.skysignals.scheduler;

import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.event.SkyEventManager;
import com.valorsky.skysignals.model.SkyEventType;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

public final class SignalScheduler {

    private final JavaPlugin plugin;
    private final Config config;
    private final SkyEventManager eventManager;
    private final Logger logger;
    private BukkitTask schedulerTask;
    private boolean scheduled = false;

    public SignalScheduler(JavaPlugin plugin, Config config, SkyEventManager eventManager) {
        this.plugin = plugin;
        this.config = config;
        this.eventManager = eventManager;
        this.logger = plugin.getLogger();
    }

    public void start() {
        if (!config.schedulerEnabled()) {
            logger.info("Scheduler disabled in configuration.");
            return;
        }
        scheduleNextEvent();
        logger.info("Signal scheduler started.");
    }

    private void scheduleNextEvent() {
        if (!config.schedulerEnabled()) return;

        int minInterval = config.schedulerMinInterval();
        int maxInterval = config.schedulerMaxInterval();
        int delay = minInterval + (int) (Math.random() * (maxInterval - minInterval + 1));

        schedulerTask = Bukkit.getScheduler().runTaskLater(plugin, this::triggerRandomEvent, delay * 20L);
        scheduled = true;
        logger.info("Next SkySignal in " + delay + " seconds.");
    }

    private void triggerRandomEvent() {
        Set<SkyEventType> allowed = config.getAllowedSchedulerEvents();
        if (allowed.isEmpty()) {
            logger.warning("No events allowed in scheduler configuration.");
            scheduleNextEvent();
            return;
        }

        List<SkyEventType> enabled = allowed.stream()
                .filter(config::isEventEnabled)
                .toList();
        if (enabled.isEmpty()) {
            logger.warning("No enabled events in scheduler allowed-events list.");
            scheduleNextEvent();
            return;
        }

        SkyEventType selected = enabled.get(new Random().nextInt(enabled.size()));
        eventManager.startEvent(selected);
        scheduled = false;
        scheduleNextEvent();
    }

    public void stop() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
        }
        scheduled = false;
    }

    public void reload() {
        stop();
        if (config.schedulerEnabled()) {
            start();
        }
    }

    public boolean isScheduled() {
        return scheduled;
    }
}
