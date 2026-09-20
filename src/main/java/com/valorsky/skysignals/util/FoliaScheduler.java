package com.valorsky.skysignals.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** Paper and Folia share these scheduler APIs. Delays supplied here are ticks. */
public final class FoliaScheduler {
    private FoliaScheduler() {}

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public static void init(Logger logger) {
        logger.info("Using Paper/Folia region and entity schedulers.");
    }

    public static void runGlobal(JavaPlugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    public static TaskHandle runGlobalDelayed(JavaPlugin plugin, Runnable task, long delay) {
        return new TaskHandle(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), Math.max(1, delay)));
    }

    public static TaskHandle runGlobalRepeating(JavaPlugin plugin, Runnable task, long delay, long period) {
        return new TaskHandle(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), Math.max(1, delay), period));
    }

    public static TaskHandle runGlobalTimer(JavaPlugin plugin, Runnable task, long delay, long period) {
        return runGlobalRepeating(plugin, task, delay, period);
    }

    public static void runRegion(JavaPlugin plugin, Location location, Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(location)) task.run();
        else Bukkit.getRegionScheduler().execute(plugin, location, task);
    }

    public static TaskHandle runRegionDelayed(JavaPlugin plugin, Location location, Runnable task, long delay) {
        return new TaskHandle(Bukkit.getRegionScheduler().runDelayed(plugin, location, t -> task.run(), Math.max(1, delay)));
    }

    public static TaskHandle runRegionRepeating(JavaPlugin plugin, Location location, Runnable task, long delay, long period) {
        return new TaskHandle(Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, t -> task.run(), Math.max(1, delay), period));
    }

    public static TaskHandle runRegionTimer(JavaPlugin plugin, Location location, Runnable task, long delay, long period) {
        return runRegionRepeating(plugin, location, task, delay, period);
    }

    public static void runEntity(JavaPlugin plugin, Entity entity, Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(entity)) task.run();
        else entity.getScheduler().run(plugin, t -> task.run(), null);
    }

    public static void runEntityDelayed(JavaPlugin plugin, Entity entity, Runnable task, long delay) {
        entity.getScheduler().runDelayed(plugin, t -> task.run(), null, Math.max(1, delay));
    }

    public static TaskHandle runEntityRepeating(JavaPlugin plugin, Entity entity, Runnable task, long delay, long period) {
        return new TaskHandle(entity.getScheduler().runAtFixedRate(plugin, t -> task.run(), null, Math.max(1, delay), period));
    }

    public static void runAsync(JavaPlugin plugin, Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
    }

    public static void runAsyncDelayed(JavaPlugin plugin, Runnable task, long delay) {
        Bukkit.getAsyncScheduler().runDelayed(plugin, t -> task.run(), Math.max(1, delay) * 50L, TimeUnit.MILLISECONDS);
    }

    public static TaskHandle runAsyncRepeating(JavaPlugin plugin, Runnable task, long delay, long period) {
        return new TaskHandle(Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(), Math.max(1, delay) * 50L, period * 50L, TimeUnit.MILLISECONDS));
    }

    public static <T> CompletableFuture<T> supplyGlobal(JavaPlugin plugin, Supplier<T> action) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            runGlobal(plugin, () -> {
                try { future.complete(action.get()); }
                catch (Exception e) { future.completeExceptionally(e); }
            });
        } catch (Exception e) { future.completeExceptionally(e); }
        return future;
    }

    public static final class TaskHandle {
        private final ScheduledTask task;
        public TaskHandle(ScheduledTask task) { this.task = task; }
        public void cancel() { if (task != null) task.cancel(); }
        public boolean isCancelled() { return task == null || task.isCancelled(); }
    }
}
