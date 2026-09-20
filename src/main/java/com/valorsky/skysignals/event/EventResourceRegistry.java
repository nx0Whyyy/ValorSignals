package com.valorsky.skysignals.event;

import com.valorsky.skysignals.util.FoliaScheduler;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class EventResourceRegistry {

    private final JavaPlugin plugin;
    private final Logger logger;
    private final Map<UUID, List<Resource>> resources = new ConcurrentHashMap<>();

    public EventResourceRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void registerEntity(UUID eventId, Entity entity) {
        register(eventId, new EntityResource(entity, plugin));
    }

    public void registerBlock(UUID eventId, Location location, BlockData originalData) {
        register(eventId, new BlockResource(location, originalData, plugin));
    }

    public void registerBossBar(UUID eventId, BossBar bossBar) {
        register(eventId, new BossBarResource(bossBar, plugin));
    }

    public void registerTask(UUID eventId, FoliaScheduler.TaskHandle task) {
        register(eventId, new TaskResource(task));
    }

    public void registerCustom(UUID eventId, Runnable cleanup) {
        register(eventId, new CustomResource(cleanup));
    }

    private void register(UUID eventId, Resource resource) {
        resources.computeIfAbsent(eventId, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(resource);
    }

    public void cleanup(UUID eventId) {
        List<Resource> eventResources = resources.remove(eventId);
        if (eventResources == null || eventResources.isEmpty()) return;

        for (Resource resource : eventResources) {
            try {
                resource.cleanup();
            } catch (Exception e) {
                logger.warning("Failed to cleanup resource for event " + eventId + ": " + e.getMessage());
            }
        }
    }

    public void cleanupAll() {
        for (UUID eventId : new ArrayList<>(resources.keySet())) {
            cleanup(eventId);
        }
    }

    private interface Resource {
        void cleanup();
    }

    private record EntityResource(Entity entity, JavaPlugin plugin) implements Resource {
        @Override
        public void cleanup() {
            if (entity != null && entity.isValid()) {
                com.valorsky.skysignals.util.FoliaScheduler.runEntity(plugin, entity, entity::remove);
            }
        }
    }

    private record BlockResource(Location location, BlockData originalData, JavaPlugin plugin) implements Resource {
        @Override
        public void cleanup() {
            if (location != null && location.getWorld() != null) {
                com.valorsky.skysignals.util.FoliaScheduler.runRegion(plugin, location, () -> location.getBlock().setBlockData(originalData));
            }
        }
    }

    private record BossBarResource(BossBar bossBar, JavaPlugin plugin) implements Resource {
        @Override
        public void cleanup() {
            if (bossBar != null && plugin != null) {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    player.hideBossBar(bossBar);
                }
            }
        }
    }

    private record TaskResource(FoliaScheduler.TaskHandle task) implements Resource {
        @Override
        public void cleanup() {
            if (task != null) {
                task.cancel();
            }
        }
    }

    private record CustomResource(Runnable cleanupAction) implements Resource {
        @Override
        public void cleanup() {
            if (cleanupAction != null) {
                cleanupAction.run();
            }
        }
    }
}