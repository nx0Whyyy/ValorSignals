package com.valorsky.skysignals.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;

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
                Object sched = Bukkit.class.getMethod("getGlobalScheduler").invoke(null);
                if (sched != null) {
                    sched.getClass().getMethod("run", JavaPlugin.class, Runnable.class).invoke(sched, plugin, task);
                    return;
                }
            } catch (Exception ignored) {}
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public static void runGlobalDelayed(JavaPlugin plugin, Runnable task, long delay) {
        if (isFolia()) {
            try {
                Object sched = Bukkit.class.getMethod("getGlobalScheduler").invoke(null);
                if (sched != null) {
                    sched.getClass().getMethod("runDelayed", JavaPlugin.class, Runnable.class, long.class)
                            .invoke(sched, plugin, task, delay);
                    return;
                }
            } catch (Exception ignored) {}
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }

    public static TaskHandle runGlobalRepeating(JavaPlugin plugin, Runnable task, long delay, long period) {
        return runGlobalRepeatingTask(plugin, task, delay, period);
    }

    public static TaskHandle runGlobalTimer(JavaPlugin plugin, Runnable task, long delay, long period) {
        return runGlobalRepeatingTask(plugin, task, delay, period);
    }

    private static TaskHandle runGlobalRepeatingTask(JavaPlugin plugin, Runnable task, long delay, long period) {
        if (isFolia()) {
            try {
                Object sched = Bukkit.class.getMethod("getGlobalScheduler").invoke(null);
                if (sched != null) {
                    Object handle = sched.getClass()
                            .getMethod("runAtFixedRate", JavaPlugin.class, Runnable.class, long.class, long.class)
                            .invoke(sched, plugin, task, delay, period);
                    return new TaskHandle(handle);
                }
            } catch (Exception ignored) {}
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        return new TaskHandle(bukkitTask);
    }

    public static void runRegion(JavaPlugin plugin, Location location, Runnable task) {
        if (isFolia()) {
            try {
                World world = location.getWorld();
                if (world != null) {
                    CompletableFuture<?> future = world.getChunkAtAsync(location);
                    Object chunk = future.getNow(null);
                    if (chunk != null) {
                        Object scheduler = chunk.getClass().getMethod("getScheduler").invoke(chunk);
                        if (scheduler != null) {
                            scheduler.getClass()
                                    .getMethod("run", JavaPlugin.class, Location.class, Runnable.class)
                                    .invoke(scheduler, plugin, location, task);
                            return;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public static void runRegionDelayed(JavaPlugin plugin, Location location, Runnable task, long delay) {
        if (isFolia()) {
            try {
                World world = location.getWorld();
                if (world != null) {
                    CompletableFuture<?> future = world.getChunkAtAsync(location);
                    Object chunk = future.getNow(null);
                    if (chunk != null) {
                        Object scheduler = chunk.getClass().getMethod("getScheduler").invoke(chunk);
                        if (scheduler != null) {
                            scheduler.getClass()
                                    .getMethod("runDelayed", JavaPlugin.class, Location.class, Runnable.class, long.class)
                                    .invoke(scheduler, plugin, location, task, delay);
                            return;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }

    public static TaskHandle runRegionRepeating(JavaPlugin plugin, Location location, Runnable task, long delay, long period) {
        return runRegionRepeatingTask(plugin, location, task, delay, period);
    }

    public static TaskHandle runRegionTimer(JavaPlugin plugin, Location location, Runnable task, long delay, long period) {
        return runRegionRepeatingTask(plugin, location, task, delay, period);
    }

    private static TaskHandle runRegionRepeatingTask(JavaPlugin plugin, Location location, Runnable task, long delay, long period) {
        if (isFolia()) {
            try {
                World world = location.getWorld();
                if (world != null) {
                    CompletableFuture<?> future = world.getChunkAtAsync(location);
                    Object chunk = future.getNow(null);
                    if (chunk != null) {
                        Object scheduler = chunk.getClass().getMethod("getScheduler").invoke(chunk);
                        if (scheduler != null) {
                            Object handle = scheduler.getClass()
                                    .getMethod("runAtFixedRate", JavaPlugin.class, Location.class, Runnable.class, long.class, long.class)
                                    .invoke(scheduler, plugin, location, task, delay, period);
                            return new TaskHandle(handle);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        return new TaskHandle(bukkitTask);
    }

    public static void runEntity(JavaPlugin plugin, Entity entity, Runnable task) {
        if (isFolia()) {
            try {
                Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
                if (scheduler != null) {
                    scheduler.getClass()
                            .getMethod("run", JavaPlugin.class, Runnable.class)
                            .invoke(scheduler, plugin, task);
                    return;
                }
            } catch (Exception ignored) {}
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public static void runEntityDelayed(JavaPlugin plugin, Entity entity, Runnable task, long delay) {
        if (isFolia()) {
            try {
                Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
                if (scheduler != null) {
                    scheduler.getClass()
                            .getMethod("runDelayed", JavaPlugin.class, Runnable.class, long.class)
                            .invoke(scheduler, plugin, task, delay);
                    return;
                }
            } catch (Exception ignored) {}
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }

    public static void runAsync(JavaPlugin plugin, Runnable task) {
        if (isFolia()) {
            try {
                Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
                if (asyncScheduler != null) {
                    asyncScheduler.getClass()
                            .getMethod("runNow", JavaPlugin.class, Runnable.class)
                            .invoke(asyncScheduler, plugin, task);
                    return;
                }
            } catch (Exception ignored) {}
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public static void runAsyncDelayed(JavaPlugin plugin, Runnable task, long delay) {
        if (isFolia()) {
            try {
                Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
                if (asyncScheduler != null) {
                    asyncScheduler.getClass()
                            .getMethod("runDelayed", JavaPlugin.class, Runnable.class, long.class)
                            .invoke(asyncScheduler, plugin, task, delay);
                    return;
                }
            } catch (Exception ignored) {}
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }

    public static TaskHandle runAsyncRepeating(JavaPlugin plugin, Runnable task, long interval, long period) {
        if (isFolia()) {
            try {
                Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
                if (asyncScheduler != null) {
                    Object handle = asyncScheduler.getClass()
                            .getMethod("runAtFixedRate", JavaPlugin.class, Runnable.class, long.class, long.class)
                            .invoke(asyncScheduler, plugin, task, interval, period);
                    return new TaskHandle(handle);
                }
            } catch (Exception ignored) {}
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, interval, period);
        return new TaskHandle(bukkitTask);
    }

    public static class TaskHandle {
        private BukkitTask bukkitTask;
        private Object foliaTask;

        public TaskHandle(BukkitTask task) {
            this.bukkitTask = task;
        }

        public TaskHandle(Object foliaTask) {
            this.foliaTask = foliaTask;
        }

        public void cancel() {
            if (bukkitTask != null && !bukkitTask.isCancelled()) {
                bukkitTask.cancel();
            }
            if (foliaTask != null) {
                try {
                    foliaTask.getClass().getMethod("cancel").invoke(foliaTask);
                } catch (Exception ignored) {}
            }
        }

        public boolean isCancelled() {
            if (bukkitTask != null) return bukkitTask.isCancelled();
            if (foliaTask != null) {
                try {
                    return (boolean) foliaTask.getClass().getMethod("isCancelled").invoke(foliaTask);
                } catch (Exception e) {
                    return true;
                }
            }
            return true;
        }
    }
}
