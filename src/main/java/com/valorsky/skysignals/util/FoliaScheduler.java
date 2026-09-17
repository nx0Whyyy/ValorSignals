package com.valorsky.skysignals.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public final class FoliaScheduler {

    private static Logger logger;

    private FoliaScheduler() {}

    private static Boolean foliaDetected = null;

    public static boolean isFolia() {
        if (foliaDetected == null) {
            try {
                Class.forName("org.bukkit.scheduler.GlobalRegionScheduler");
                foliaDetected = true;
            } catch (ClassNotFoundException e) {
                try {
                    Class.forName("org.bukkit.scheduler.ScheduledTask");
                    foliaDetected = true;
                } catch (ClassNotFoundException e2) {
                    foliaDetected = false;
                }
            }
        }
        return foliaDetected;
    }

    private static void setLogger(Logger log) {
        logger = log;
    }

    public static void init(Logger log) {
        setLogger(log);
        if (isFolia()) {
            log.info("[FoliaScheduler] Detected Folia server. Using Folia schedulers.");
        }
    }

    private static Object getGlobalRegionScheduler() {
        try {
            Method method = Bukkit.class.getMethod("getGlobalRegionScheduler");
            return method.invoke(null);
        } catch (Exception e1) {
            try {
                Method method = Bukkit.class.getMethod("getGlobalScheduler");
                return method.invoke(null);
            } catch (Exception e2) {
                try {
                    return Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
                } catch (Exception e3) {
                    return null;
                }
            }
        }
    }

    private static Object getAsyncScheduler() {
        try {
            return Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
        } catch (Exception e1) {
            try {
                return Bukkit.getServer().getClass().getMethod("getAsyncScheduler").invoke(Bukkit.getServer());
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private static Object getRegionScheduler(Location location) {
        World world = location.getWorld();
        if (world == null) return null;
        try {
            CompletableFuture<?> future = world.getChunkAtAsync(location);
            Object chunk = future.getNow(null);
            if (chunk != null) {
                Method getScheduler = chunk.getClass().getMethod("getScheduler");
                return getScheduler.invoke(chunk);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static Object getEntityScheduler(Entity entity) {
        try {
            Method getScheduler = entity.getClass().getMethod("getScheduler");
            return getScheduler.invoke(entity);
        } catch (Exception e) {
            return null;
        }
    }

    public static void runGlobal(JavaPlugin plugin, Runnable task) {
        if (isFolia()) {
            try {
                Object sched = getGlobalRegionScheduler();
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
                Object sched = getGlobalRegionScheduler();
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
        if (isFolia()) {
            try {
                Object sched = getGlobalRegionScheduler();
                if (sched != null) {
                    Object handle = sched.getClass()
                            .getMethod("runAtFixedRate", JavaPlugin.class, Runnable.class, long.class, long.class)
                            .invoke(sched, plugin, task, delay, period);
                    return new TaskHandle(handle);
                }
            } catch (Exception e) {
                if (logger != null) logger.warning("[FoliaScheduler] runGlobalRepeating failed: " + e);
            }
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        return new TaskHandle(bukkitTask);
    }

    public static TaskHandle runGlobalTimer(JavaPlugin plugin, Runnable task, long delay, long period) {
        return runGlobalRepeating(plugin, task, delay, period);
    }

    public static void runRegion(JavaPlugin plugin, Location location, Runnable task) {
        if (isFolia()) {
            try {
                Object sched = getRegionScheduler(location);
                if (sched != null) {
                    sched.getClass()
                            .getMethod("run", JavaPlugin.class, Location.class, Runnable.class)
                            .invoke(sched, plugin, location, task);
                    return;
                }
            } catch (Exception ignored) {}
            runGlobal(plugin, task);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public static void runRegionDelayed(JavaPlugin plugin, Location location, Runnable task, long delay) {
        if (isFolia()) {
            try {
                Object sched = getRegionScheduler(location);
                if (sched != null) {
                    sched.getClass()
                            .getMethod("runDelayed", JavaPlugin.class, Location.class, Runnable.class, long.class)
                            .invoke(sched, plugin, location, task, delay);
                    return;
                }
            } catch (Exception ignored) {}
            runGlobalDelayed(plugin, task, delay);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }

    public static TaskHandle runRegionRepeating(JavaPlugin plugin, Location location, Runnable task, long delay, long period) {
        if (isFolia()) {
            try {
                Object sched = getRegionScheduler(location);
                if (sched != null) {
                    Object handle = sched.getClass()
                            .getMethod("runAtFixedRate", JavaPlugin.class, Location.class, Runnable.class, long.class, long.class)
                            .invoke(sched, plugin, location, task, delay, period);
                    return new TaskHandle(handle);
                }
            } catch (Exception ignored) {}
            return runGlobalRepeating(plugin, task, delay, period);
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        return new TaskHandle(bukkitTask);
    }

    public static TaskHandle runRegionTimer(JavaPlugin plugin, Location location, Runnable task, long delay, long period) {
        return runRegionRepeating(plugin, location, task, delay, period);
    }

    public static void runEntity(JavaPlugin plugin, Entity entity, Runnable task) {
        if (isFolia()) {
            try {
                Object sched = getEntityScheduler(entity);
                if (sched != null) {
                    sched.getClass()
                            .getMethod("run", JavaPlugin.class, Runnable.class)
                            .invoke(sched, plugin, task);
                    return;
                }
            } catch (Exception ignored) {}
            runGlobal(plugin, task);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public static void runEntityDelayed(JavaPlugin plugin, Entity entity, Runnable task, long delay) {
        if (isFolia()) {
            try {
                Object sched = getEntityScheduler(entity);
                if (sched != null) {
                    sched.getClass()
                            .getMethod("runDelayed", JavaPlugin.class, Runnable.class, long.class)
                            .invoke(sched, plugin, task, delay);
                    return;
                }
            } catch (Exception ignored) {}
            runGlobalDelayed(plugin, task, delay);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }

    public static void runAsync(JavaPlugin plugin, Runnable task) {
        if (isFolia()) {
            try {
                Object asyncSched = getAsyncScheduler();
                if (asyncSched != null) {
                    asyncSched.getClass()
                            .getMethod("runNow", JavaPlugin.class, Runnable.class)
                            .invoke(asyncSched, plugin, task);
                    return;
                }
            } catch (Exception ignored) {}
        }
        Thread t = new Thread(task, "SkySignals-Async-Fallback");
        t.setDaemon(true);
        t.start();
    }

    public static void runAsyncDelayed(JavaPlugin plugin, Runnable task, long delay) {
        if (isFolia()) {
            try {
                Object asyncSched = getAsyncScheduler();
                if (asyncSched != null) {
                    asyncSched.getClass()
                            .getMethod("runDelayed", JavaPlugin.class, Runnable.class, long.class)
                            .invoke(asyncSched, plugin, task, delay);
                    return;
                }
            } catch (Exception ignored) {}
        }
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(delay * 50L);
            } catch (InterruptedException ignored) {}
            task.run();
        }, "SkySignals-Async-Delayed");
        t.setDaemon(true);
        t.start();
    }

    public static TaskHandle runAsyncRepeating(JavaPlugin plugin, Runnable task, long interval, long period) {
        if (isFolia()) {
            try {
                Object asyncSched = getAsyncScheduler();
                if (asyncSched != null) {
                    Object handle = asyncSched.getClass()
                            .getMethod("runAtFixedRate", JavaPlugin.class, Runnable.class, long.class, long.class)
                            .invoke(asyncSched, plugin, task, interval, period);
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
