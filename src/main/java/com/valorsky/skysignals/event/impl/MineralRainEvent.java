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
    private final Map<UUID, Item> items = new ConcurrentHashMap<>();
    private final Map<UUID, TaskHandle> trails = new ConcurrentHashMap<>();
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
        locationService.findNearPlayersAsync(30, 150).whenComplete((location, error) -> {
            if (!plugin.isEnabled()) return;
            FoliaScheduler.runGlobal(plugin, () -> {
                if (getStatus() != SkyEventStatus.ANNOUNCING) return;
                if (error != null || location.isEmpty()) {
                    logger.warning("No safe loaded location for " + getType());
                    cancel();
                    return;
                }
                Location loc = location.get();
                rainCenter = loc;

                notificationService.notifyEventPhase(this, "start");
                soundService.playGlobal("mineral_rain_start");
            });
        });
    }

    @Override
    public boolean isReady() { return rainCenter != null; }

    private void startRain() {
        if (rainCenter == null) return;

        spawnTask = FoliaScheduler.runGlobalTimer(plugin, () -> {
            for (Item item : items.values()) if (!item.isValid()) removeItem(item);
            if (activeItems.get() >= maxActiveItems) return;
            if (rainCenter == null || rainCenter.getWorld() == null) return;

            spawnMineralDrop();
        }, 0L, spawnInterval * 20L);

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
        FoliaScheduler.runRegion(plugin, spawnLoc, () -> {
        if (cancelled || getStatus() == SkyEventStatus.FINISHED || getStatus() == SkyEventStatus.COMPLETING) {
            activeItems.decrementAndGet(); return;
        }
        Item item = world.dropItemNaturally(spawnLoc, stack);
        items.put(item.getUniqueId(), item);
        item.setVelocity(new Vector(0, -0.5, 0));
        item.setPickupDelay(rewardService.rewardsAllowed(id) ? 20 : Integer.MAX_VALUE);
        item.setPersistent(false);

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
        TaskHandle trail = FoliaScheduler.runEntityRepeating(plugin, item, new Runnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!item.isValid() || ticks > 100) {
                    removeItem(item);
                    return;
                }
                if (item.isOnGround()) { ticks++; return; }
                List<Player> audience = getNearbyPlayers(item.getLocation(), 48);
                if (!audience.isEmpty()) {
                    particleService.spawnCircle(item.getLocation(), Particle.HAPPY_VILLAGER, 1, 3, 0.02, audience);
                }
                ticks++;
            }
        }, 1L, 2L);

        trails.put(item.getUniqueId(), trail);
        soundService.play("mineral_rain_drop", spawnLoc, getNearbyPlayers(spawnLoc, 48));
        });
    }

    private void removeItem(Item item) {
        TaskHandle trail = trails.remove(item.getUniqueId());
        if (trail != null) trail.cancel();
        if (items.remove(item.getUniqueId()) != null) activeItems.decrementAndGet();
        FoliaScheduler.runEntity(plugin, item, item::remove);
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
        for (Item item : items.values()) removeItem(item);
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