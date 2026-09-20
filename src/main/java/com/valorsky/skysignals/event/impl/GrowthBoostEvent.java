package com.valorsky.skysignals.event.impl;

import com.valorsky.skysignals.event.AbstractSkyEvent;
import com.valorsky.skysignals.event.EventContext;
import com.valorsky.skysignals.model.*;
import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.notification.NotificationService;
import com.valorsky.skysignals.particle.ParticleService;
import com.valorsky.skysignals.particle.CircleShape;
import com.valorsky.skysignals.sound.SoundService;
import com.valorsky.skysignals.util.FoliaScheduler;
import com.valorsky.skysignals.util.FoliaScheduler.TaskHandle;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class GrowthBoostEvent extends AbstractSkyEvent implements Listener {

    private final NotificationService notificationService;
    private final ParticleService particleService;
    private final SoundService soundService;
    private final Logger logger;

    private final double multiplier;
    private final int visualCooldown;
    private TaskHandle visualTask;
    private final Set<Location> boostedBlocks = ConcurrentHashMap.newKeySet();
    private final Map<Location, Long> lastVisual = new ConcurrentHashMap<>();

    public GrowthBoostEvent(EventState state, JavaPlugin plugin, NotificationService notificationService,
                            ParticleService particleService, SoundService soundService, Config config) {
        super(state, plugin);
        this.notificationService = notificationService;
        this.particleService = particleService;
        this.soundService = soundService;
        this.logger = plugin.getLogger();
        this.multiplier = config.getGrowthBoostMultiplier();
        this.visualCooldown = config.getGrowthBoostVisualCooldown();

        // Register listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public List<EventLocation> getEventLocations() {
        return plugin.getServer().getWorlds().stream()
                .map(world -> EventLocation.world(world.getName(), "Cultures dans tout le monde"))
                .toList();
    }

    @Override
    protected void onAnnouncing() {
        this.status = SkyEventStatus.ANNOUNCING;
        soundService.playGlobal("growth_boost_start");
    }

    @Override
    protected void onWarning() {
        this.status = SkyEventStatus.WARNING;
    }

    @Override
    protected void onActive() {
        this.status = SkyEventStatus.ACTIVE;

        // Periodic visual effects on random crops
        visualTask = FoliaScheduler.runGlobalTimer(plugin, this::showVisualEffects, 100L, 100L);

        logger.info("Growth boost event started with multiplier " + multiplier);
    }

    @Override
    protected void onCompleting() {
        this.status = SkyEventStatus.COMPLETING;
        if (visualTask != null) visualTask.cancel();
    }

    @Override
    protected void onFinished() {
        this.status = SkyEventStatus.FINISHED;
        HandlerList.unregisterAll(this);
    }

    @Override
    protected void onCancelled() {
        this.status = SkyEventStatus.CANCELLED;
        if (visualTask != null) visualTask.cancel();
        HandlerList.unregisterAll(this);
    }

    private void showVisualEffects() {
        long now = System.currentTimeMillis();
        lastVisual.entrySet().removeIf(entry -> now - entry.getValue() > Math.max(1000, visualCooldown * 50L));
        for (Player player : Bukkit.getOnlinePlayers()) {
            FoliaScheduler.runEntity(plugin, player, () -> {
                if (getStatus() != SkyEventStatus.ACTIVE) return;
                Location center = player.getLocation();
                for (int x = -10; x <= 10; x++) {
                    for (int z = -10; z <= 10; z++) {
                        Location loc = center.clone().add(x, 0, z);
                        // Do not read or load a neighbouring region from the player's thread.
                        if (!Bukkit.isOwnedByCurrentRegion(loc) || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;
                        if (isGrowable(loc.getBlock())) {
                            particleService.spawnCircle(loc, Particle.HAPPY_VILLAGER, 2, 5, 0.05, List.of(player));
                        }
                    }
                }
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        if (getStatus() != SkyEventStatus.ACTIVE) return;

        Block block = event.getBlock();
        if (!isGrowable(block)) return;

        // Apply growth boost by advancing age multiple times
        if (event.getNewState().getBlockData() instanceof Ageable ageable) {
            int maxAge = ageable.getMaximumAge();
            int currentAge = ageable.getAge();
            int newAge = Math.min(maxAge, currentAge + Math.max(0, (int) Math.ceil(multiplier) - 1));

            if (newAge > currentAge) {
                ageable.setAge(newAge);
                event.getNewState().setBlockData(ageable);

                // Visual feedback
                Location loc = block.getLocation();
                Long last = lastVisual.get(loc);
                long now = System.currentTimeMillis();
                if (last == null || now - last > visualCooldown * 50L) {
                    lastVisual.put(loc, now);
                    List<Player> audience = getNearbyPlayers(loc, 32);
                    if (!audience.isEmpty()) {
                        particleService.spawnCircle(loc, Particle.HAPPY_VILLAGER, 3, 8, 0.1, audience);
                        soundService.play("growth_boost_tick", loc, audience);
                    }
                }

                // Notify player if nearby
                for (Player player : getNearbyPlayers(loc, 16)) {
                    // Could send action bar message
                }
            }
        }
    }

    private boolean isGrowable(Block block) {
        Material type = block.getType();
        return type == Material.WHEAT || type == Material.CARROTS || type == Material.POTATOES
            || type == Material.BEETROOTS || type == Material.NETHER_WART
            || type == Material.MELON_STEM || type == Material.PUMPKIN_STEM
            || type == Material.COCOA || type == Material.SWEET_BERRY_BUSH
            || type == Material.CAVE_VINES || type == Material.CAVE_VINES_PLANT
            || type == Material.KELP || type == Material.KELP_PLANT
            || type == Material.BAMBOO || type == Material.BAMBOO_SAPLING
            || type == Material.SUGAR_CANE || type == Material.CACTUS;
    }

    private List<Player> getNearbyPlayers(Location center, double radius) {
        if (center == null || center.getWorld() == null) return List.of();
        double radiusSq = radius * radius;
        return center.getWorld().getPlayers().stream()
            .filter(p -> p.getLocation().distanceSquared(center) <= radiusSq)
            .toList();
    }

    @Override
    public void tick(long elapsedSeconds) {
        super.tick(elapsedSeconds);
    }

    @Override
    public void stop() {
        if (visualTask != null) visualTask.cancel();
        HandlerList.unregisterAll(this);
        lastVisual.clear();
    }

    @Override
    public void cancel() {
        super.cancel();
        stop();
    }

    @Override
    protected Map<String, Object> getExtraData() {
        return Map.of(
            "multiplier", multiplier,
            "visualCooldown", visualCooldown
        );
    }
}