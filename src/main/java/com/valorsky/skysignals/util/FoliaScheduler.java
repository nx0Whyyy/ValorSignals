package com.valorsky.skysignals.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class FoliaScheduler {

    private FoliaScheduler() {}

    private static Boolean foliaDetected = null;

    private static boolean isFolia() {
        if (foliaDetected == null) {
            try {
                Class.forName("org.bukkit.scheduler.GlobalScheduler");
                foliaDetected = true;
            } catch (ClassNotFoundException e) {
                foliaDetected = false;
            }
        }
        return foliaDetected;
    }

    public static void runGlobal(JavaPlugin plugin, Runnable task) {
        if (isFolia()) {
            try {
                Object globalScheduler = Bukkit.class.getMethod("getGlobalScheduler").invoke(null);
                globalScheduler.getClass().getMethod("run", JavaPlugin.class, Runnable.class).invoke(globalScheduler, plugin, task);
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, task);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runGlobalDelayed(JavaPlugin plugin, Runnable task, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }

    public static TaskHandle runGlobalTimer(JavaPlugin plugin, Runnable task, long delay, long period) {
        if (isFolia()) {
            try {
                Object globalScheduler = Bukkit.class.getMethod("getGlobalScheduler").invoke(null);
                Object handle = globalScheduler.getClass().getMethod("runAtFixedRate", JavaPlugin.class, Runnable.class, long.class, long.class).invoke(globalScheduler, plugin, task, delay, period);
                return new TaskHandle(handle);
            } catch (Exception e) {
                BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
                return new TaskHandle(bukkitTask);
            }
        } else {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
            return new TaskHandle(bukkitTask);
        }
    }

    public static void runRegion(JavaPlugin plugin, Location location, Runnable task) {
        World world = location.getWorld();
        if (world == null) {
            runGlobal(plugin, task);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public static void runRegionDelayed(JavaPlugin plugin, Location location, Runnable task, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }

    public static TaskHandle runRegionTimer(JavaPlugin plugin, Location location, Runnable task, long delay, long period) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        return new TaskHandle(bukkitTask);
    }

    public static void runEntity(JavaPlugin plugin, Entity entity, Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public static void runEntityDelayed(JavaPlugin plugin, Entity entity, Runnable task, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }

    public static TaskHandle runEntityTimer(JavaPlugin plugin, Entity entity, Runnable task, long delay, long period) {
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        return new TaskHandle(bukkitTask);
    }

    public static void runAsync(JavaPlugin plugin, Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public static void runAsyncDelayed(JavaPlugin plugin, Runnable task, long delay) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, 1L);
    }

    public static class TaskHandle {
        private BukkitTask bukkitTask;
        private Object foliaHandle;

        public TaskHandle(BukkitTask task) {
            this.bukkitTask = task;
        }

        public TaskHandle(Object foliaHandle) {
            this.foliaHandle = foliaHandle;
        }

        public void cancel() {
            if (bukkitTask != null && !bukkitTask.isCancelled()) {
                bukkitTask.cancel();
            }
            if (foliaHandle != null) {
                try {
                    foliaHandle.getClass().getMethod("cancel").invoke(foliaHandle);
                } catch (Exception ignored) {}
            }
        }

        public boolean isCancelled() {
            if (bukkitTask != null) return bukkitTask.isCancelled();
            if (foliaHandle != null) {
                try {
                    return (boolean) foliaHandle.getClass().getMethod("isCancelled").invoke(foliaHandle);
                } catch (Exception e) {
                    return true;
                }
            }
            return true;
        }
    }
}
