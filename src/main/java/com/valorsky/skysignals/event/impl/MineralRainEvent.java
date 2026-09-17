package com.valorsky.skysignals.event.impl;

import com.valorsky.skysignals.event.AbstractSkyEvent;
import com.valorsky.skysignals.event.EventContext;
import com.valorsky.skysignals.event.SkyEventManager;
import com.valorsky.skysignals.location.SafeLocationService;
import com.valorsky.skysignals.model.*;
import com.valorsky.skysignals.notification.NotificationService;
import com.valorsky.skysignals.particle.ParticleService;
import com.valorsky.skysignals.particle.CircleShape;
import com.valorsky.skysignals.reward.RewardService;
import com.valorsky.skysignals.sound.SoundService;
import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.util.FoliaScheduler;
import com.valorsky.skysignals.util.FoliaScheduler.TaskHandle;
import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public final class MineralRainEvent extends AbstractSkyEvent {

    private final NotificationService notificationService;
    private final RewardService rewardService;
    private final ParticleService particleService;
    private final SoundService soundService;
    private final SafeLocationService locationService;
    private final Config config;
    private final Logger logger;

    private Location rainCenter;
    private TaskHandle spawnTask;
    private TaskHandle cleanupTask;
    private final AtomicInteger activeItems = new AtomicInteger(0);
    private final int maxActiveItems;
    private final int spawnInterval;
    private final Map<Material, Integer> dropWeights;
    private final Set<UUID> rewardedPlayers = ConcurrentHashMap.newKeySet();

    public MineralRainEvent(EventState state, JavaPlugin plugin, NotificationService notificationService,
                            RewardService rewardService, ParticleService particleService,
                            SoundService soundService, SafeLocationService locationService, Config config) {
        super(state, plugin);
        this.notificationService = notificationService;
        this.rewardService = rewardService;
        this.particleService = particleService;
        this.soundService = soundService;
        this.locationService = locationService;
        this.config = config;
        this.logger = plugin.getLogger();
        this.maxActiveItems = config.getMineralRainMaxActiveItems();
        this.spawnInterval = config.getMineralRainSpawnInterval();
        this.dropWeights = config.getMineralRainDrops();
    }

    @Override
    protected void onAnnouncing() {
        this.status = SkyEventStatus.ANNOUNCING;
        findRainLocation();
    }

    @Override
    protected void onWarning() {
        this.status = SkyEventStatus.WARNING;
        startRain();
    }

    @Override
    protected void onActive() {
        this.status = SkyEventStatus.ACTIVE;
    }

    @Override
    protected void onCompleting() {
        this.status = SkyEventStatus.COMPLETING;
        stopRain();
    }

    @Override
    protected void onFinished() {
        this.status = SkyEventStatus.FINISHED;
        cleanupItems();
    }

    @Override
    protected void onCancelled() {
        this.status = SkyEventStatus.CANCELLED;
        cleanupItems();
    }

    private void findRainLocation() {
        Player firstPlayer = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        Location playerLoc = firstPlayer != null ? firstPlayer.getLocation() : null;
        locationService.findSafeLocation(playerLoc, 30, 150).ifPresentOrElse(loc -> {
            rainCenter = loc;
            notificationService.notifyEventPhase(this, "start");
            soundService.playGlobal("mineral_rain_start");
        }, () -> {
            logger.warning("Could not find safe location for mineral rain. Cancelling.");
            this.status = SkyEventStatus.CANCELLED;
        });
    }

    private void startRain() {
        if (rainCenter == null) return;

        spawnTask = FoliaScheduler.runGlobalTimer(plugin, () -> {
            if (activeItems.get() >= maxActiveItems) return;
            if (rainCenter == null || rainCenter.getWorld() == null) return;

            spawnMineralDrop();
        }, 0L, spawnInterval * 20L);

        // Auto-stop after duration
        int duration = config.getEventDuration(SkyEventType.MINERAL_RAIN);
        FoliaScheduler.runGlobalDelayed(plugin, () -> {
            if (getStatus() == SkyEventStatus.ACTIVE) {
                ((SkyEventManager) plugin.getServer().getPluginManager().getPlugin("SkySignals")).transitionPhase(this, SkyEventPhase.COMPLETING);
            }
        }, duration * 20L);

        logger.info("Mineral rain started at " + rainCenter.getWorld().getName());
    }

    private void spawnMineralDrop() {
        if (rainCenter == null || rainCenter.getWorld() == null) return;
        if (activeItems.incrementAndGet() > maxActiveItems) {
            activeItems.decrementAndGet();
            return;
        }

        World world = rainCenter.getWorld();
        // Random position in 20-block radius
        double angle = Math.random() * 2 * Math.PI;
        double distance = Math.random() * 20;
        Location spawnLoc = rainCenter.clone().add(
            distance * Math.cos(angle), 30, distance * Math.sin(angle)
        );

        // Pick random material based on weights
        Material material = pickRandomMaterial();
        if (material == null) material = Material.IRON_INGOT;

        ItemStack stack = new ItemStack(material, 1 + (int)(Math.random() * 3));
        Item item = world.dropItemNaturally(spawnLoc, stack);
        item.setVelocity(new Vector(0, -0.5, 0));
        item.setPickupDelay(20);

        // Mark as event item
        item.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "skysignals_event"),
            PersistentDataType.STRING, id.toString()
        );
        item.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "skysignals_mineral"),
            PersistentDataType.BYTE, (byte) 1
        );

        // Trail particles
        FoliaScheduler.runRegionTimer(plugin, spawnLoc, new Runnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!item.isValid() || item.isOnGround() || ticks > 100) {
                    activeItems.decrementAndGet();
                    return;
                }
                List<Player> audience = getNearbyPlayers(item.getLocation(), 48);
                if (!audience.isEmpty()) {
                    particleService.spawnCircle(item.getLocation(), Particle.HAPPY_VILLAGER, 1, 3, 0.02, audience);
                }
                ticks++;
            }
        }, 1L, 2L);

        soundService.play("mineral_rain_drop", spawnLoc, getNearbyPlayers(spawnLoc, 48));
    }

    private Material pickRandomMaterial() {
        int totalWeight = dropWeights.values().stream().mapToInt(Integer::intValue).sum();
        if (totalWeight <= 0) return Material.IRON_INGOT;

        int r = new Random().nextInt(totalWeight);
        int cumulative = 0;

        for (Map.Entry<Material, Integer> entry : dropWeights.entrySet()) {
            cumulative += entry.getValue();
            if (r < cumulative) {
                return entry.getKey();
            }
        }
        return dropWeights.keySet().iterator().next();
    }

    private void stopRain() {
        if (spawnTask != null) spawnTask.cancel();

        // Start cleanup task
        cleanupTask = FoliaScheduler.runGlobalTimer(plugin, this::cleanupGroundItems, 100L, 100L);
    }

    private void cleanupGroundItems() {
        if (rainCenter == null || rainCenter.getWorld() == null) return;

        int removed = 0;
        for (org.bukkit.entity.Entity entity : rainCenter.getWorld().getNearbyEntities(rainCenter, 30, 30, 30)) {
            if (entity instanceof Item item) {
                String eventId = item.getPersistentDataContainer().get(
                    new NamespacedKey(plugin, "skysignals_event"),
                    PersistentDataType.STRING
                );
                if (id.toString().equals(eventId)) {
                    item.remove();
                    removed++;
                    activeItems.decrementAndGet();
                }
            }
        }

        if (removed == 0 && activeItems.get() == 0) {
            if (cleanupTask != null) cleanupTask.cancel();
            FoliaScheduler.runGlobalDelayed(plugin, () -> {
                if (getStatus() == SkyEventStatus.COMPLETING) {
                    ((SkyEventManager) plugin.getServer().getPluginManager().getPlugin("SkySignals")).transitionPhase(this, SkyEventPhase.FINISHED);
                }
            }, 20L);
        }
    }

    private void cleanupItems() {
        cleanupGroundItems();
        if (spawnTask != null) spawnTask.cancel();
        if (cleanupTask != null) cleanupTask.cancel();
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
        cleanupItems();
    }

    @Override
    public void cancel() {
        super.cancel();
        stop();
    }

    @Override
    protected Map<String, Object> getExtraData() {
        return Map.of(
            "centerX", rainCenter != null ? rainCenter.getBlockX() : 0,
            "centerY", rainCenter != null ? rainCenter.getBlockY() : 0,
            "centerZ", rainCenter != null ? rainCenter.getBlockZ() : 0,
            "activeItems", activeItems.get(),
            "maxItems", maxActiveItems
        );
    }
}