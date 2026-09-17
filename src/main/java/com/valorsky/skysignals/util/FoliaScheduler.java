package com.valorsky.skysignals.util;

import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Consumer;

public final class FoliaScheduler {

    private FoliaScheduler() {}

    public static void runGlobal(JavaPlugin plugin, Runnable task) {
        GlobalRegionScheduler scheduler = Bukkit.getGlobalRegionScheduler();
        scheduler.run(plugin, scheduledTask -> task.run());
    }

    public static void runGlobalDelayed(JavaPlugin plugin, Runnable task, long delay) {
        GlobalRegionScheduler scheduler = Bukkit.getGlobalRegionScheduler();
        scheduler.runDelayed(plugin, scheduledTask -> task.run(), delay);
    }

    public static TaskHandle runGlobalTimer(JavaPlugin plugin, Runnable task, long delay, long period) {
        GlobalRegionScheduler scheduler = Bukkit.getGlobalRegionScheduler();
        ScheduledTask scheduledTask = scheduler.runAtFixedRate(plugin, st -> task.run(), delay, period);
        return new ScheduledTaskHandle(scheduledTask);
    }

    public static void runRegion(JavaPlugin plugin, Location location, Runnable task) {
        World world = location.getWorld();
        if (world == null) {
            runGlobal(plugin, task);
            return;
        }
        RegionScheduler scheduler = Bukkit.getRegionScheduler();
        scheduler.run(plugin, world, location.getBlockX(), location.getBlockZ(), scheduledTask -> task.run());
    }

    public static void runRegionDelayed(JavaPlugin plugin, Location location, Runnable task, long delay) {
        World world = location.getWorld();
        if (world == null) {
            runGlobalDelayed(plugin, task, delay);
            return;
        }
        RegionScheduler scheduler = Bukkit.getRegionScheduler();
        scheduler.runDelayed(plugin, world, location.getBlockX(), location.getBlockZ(), scheduledTask -> task.run(), delay);
    }

    public static TaskHandle runRegionTimer(JavaPlugin plugin, Location location, Runnable task, long delay, long period) {
        World world = location.getWorld();
        if (world == null) {
            return runGlobalTimer(plugin, task, delay, period);
        }
        RegionScheduler scheduler = Bukkit.getRegionScheduler();
        ScheduledTask scheduledTask = scheduler.runAtFixedRate(plugin, world, location.getBlockX(), location.getBlockZ(), st -> task.run(), delay, period);
        return new ScheduledTaskHandle(scheduledTask);
    }

    public static void runEntity(JavaPlugin plugin, Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }

    public static void runEntityDelayed(JavaPlugin plugin, Entity entity, Runnable task, long delay) {
        entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delay);
    }

    public static TaskHandle runEntityTimer(JavaPlugin plugin, Entity entity, Runnable task, long delay, long period) {
        ScheduledTask scheduledTask = entity.getScheduler().runAtFixedRate(plugin, st -> task.run(), null, delay, period);
        return new ScheduledTaskHandle(scheduledTask);
    }

    public static void runAsync(JavaPlugin plugin, Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public static void cancelTask(TaskHandle task) {
        if (task != null) task.cancel();
    }

    public static void cancelTask(BukkitTask task) {
        if (task != null) task.cancel();
    }

    public interface TaskHandle {
        void cancel();
        boolean isCancelled();
    }

    private static class BukkitTaskHandle implements TaskHandle {
        private final BukkitTask task;
        BukkitTaskHandle(BukkitTask task) { this.task = task; }
        @Override public void cancel() { task.cancel(); }
        @Override public boolean isCancelled() { return task.isCancelled(); }
    }

    private static class ScheduledTaskHandle implements TaskHandle {
        private final ScheduledTask task;
        ScheduledTaskHandle(ScheduledTask task) { this.task = task; }
        @Override public void cancel() { task.cancel(); }
        @Override public boolean isCancelled() { return task.isCancelled(); }
    }
}