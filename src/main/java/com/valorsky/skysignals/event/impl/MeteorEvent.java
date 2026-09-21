package com.valorsky.skysignals.event.impl;

import com.valorsky.skysignals.event.AbstractSkyEvent;
import com.valorsky.skysignals.model.*;
import com.valorsky.skysignals.notification.NotificationService;
import com.valorsky.skysignals.particle.ParticleService;
import com.valorsky.skysignals.reward.RewardService;
import com.valorsky.skysignals.sound.SoundService;
import com.valorsky.skysignals.util.FoliaScheduler;
import com.valorsky.skysignals.util.FoliaScheduler.TaskHandle;
import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.location.SafeLocationService;
import org.bukkit.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class MeteorEvent extends AbstractSkyEvent {

    private final NotificationService notificationService;
    private final RewardService rewardService;
    private final ParticleService particleService;
    private final SoundService soundService;
    private final SafeLocationService locationService;
    private final Config config;
    private final Logger logger;

    private Location meteorStart;
    private volatile Location meteorTarget;
    private ItemDisplay meteorDisplay;
    private TaskHandle meteorTask;
    private TaskHandle trailTask;
    private float modelRotation;
    private final AtomicBoolean hasLanded = new AtomicBoolean(false);
    private final Set<UUID> notifiedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> rewardedPlayers = ConcurrentHashMap.newKeySet();
    private String direction = "unknown";

    public MeteorEvent(EventState state, JavaPlugin plugin, NotificationService notificationService,
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
    }

    @Override
    public void start() {
        super.start();
        this.status = SkyEventStatus.SCHEDULED;
        this.phase = SkyEventPhase.SCHEDULED;
    }

    @Override
    protected void onAnnouncing() {
        this.status = SkyEventStatus.ANNOUNCING;
        findTargetAndPrepare();
    }

    @Override
    protected void onWarning() {
        this.status = SkyEventStatus.WARNING;
        startMeteorDescent();
    }

    @Override
    protected void onActive() {
        this.status = SkyEventStatus.ACTIVE;
    }

    @Override
    protected void onCompleting() {
        this.status = SkyEventStatus.COMPLETING;
        handleImpact();
    }

    @Override
    protected void onFinished() {
        this.status = SkyEventStatus.FINISHED;
        cleanupMeteor();
    }

    @Override
    protected void onCancelled() {
        this.status = SkyEventStatus.CANCELLED;
        cleanupMeteor();
    }

    private void findTargetAndPrepare() {
        locationService.findNearPlayersAsync(10, 30).whenComplete((location, error) -> {
            if (!plugin.isEnabled()) return;
            FoliaScheduler.runGlobal(plugin, () -> {
                if (getStatus() != SkyEventStatus.ANNOUNCING) return;
                if (error != null || location.isEmpty()) {
                    logger.warning("No safe loaded location for " + getType());
                    cancel();
                    return;
                }
                Location loc = location.get();
                meteorTarget = loc;

                soundService.playGlobal("meteor_start");
            });
        });
    }

    @Override
    public List<EventLocation> getEventLocations() {
        Location location = meteorTarget;
        return location == null || location.getWorld() == null ? List.of()
                : List.of(EventLocation.at(location, "Point d’impact", 0));
    }

    @Override
    public boolean isReady() { return meteorTarget != null; }

    public Optional<Location> getImpactLocation() {
        Location target = meteorTarget;
        return target == null ? Optional.empty() : Optional.of(target.clone());
    }

    private void startMeteorDescent() {
        if (meteorTarget == null) return;

        FoliaScheduler.runRegion(plugin, meteorTarget, () -> {
            if (cancelled || getStatus() == SkyEventStatus.FINISHED) return;
            meteorStart = meteorTarget.clone().add(0, 80, 0);
            meteorDisplay = spawnMeteorDisplay(meteorStart);

            soundService.play("meteor_warning", meteorStart, getNearbyPlayers(meteorStart, 48));

            trailTask = FoliaScheduler.runEntityRepeating(plugin, meteorDisplay, () -> {
                if (meteorDisplay == null || !meteorDisplay.isValid() || hasLanded.get()) {
                    if (trailTask != null) trailTask.cancel();
                    return;
                }
                Location loc = meteorDisplay.getLocation();
                List<Player> audience = getNearbyPlayers(loc, 48);
                if (!audience.isEmpty()) {
                    particleService.spawnMeteorTrail(meteorStart, loc, Particle.FLAME, 10, 0.01, audience);
                }
            }, 1L, 2L);

            meteorTask = FoliaScheduler.runEntityRepeating(plugin, meteorDisplay, () -> {
                if (meteorDisplay == null || !meteorDisplay.isValid()) {
                    handleImpact();
                    return;
                }
                Location loc = meteorDisplay.getLocation();
                List<Player> audience = getNearbyPlayers(loc, 48);
                if (!audience.isEmpty()) {
                    particleService.spawnCircle(loc, Particle.FLAME, 2, 5, 0.01, audience);
                }

                if (loc.getY() <= meteorTarget.getY() + 1.5) {
                    handleImpact();
                    return;
                }
                rotateMeteor();
                meteorDisplay.teleportAsync(loc.clone().add(0, -config.getMeteorDescentSpeed(), 0));
            }, 1L, 1L);

            logger.info("Meteor descent started at " + meteorStart.getWorld().getName());
        });
    }

    private ItemDisplay spawnMeteorDisplay(Location location) {
        return location.getWorld().spawn(location, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTeleportDuration(1);
            display.setInterpolationDuration(1);
            display.setDisplayWidth(8.0f);
            display.setDisplayHeight(8.0f);

            ItemStack item = new ItemStack(config.getMeteorModelItem());
            ItemMeta meta = item.getItemMeta();
            if (config.isMeteorModelEnabled()) {
                NamespacedKey model = NamespacedKey.fromString(config.getMeteorItemModel());
                if (model != null) meta.setItemModel(model);
                else logger.warning("Invalid meteor item model key: " + config.getMeteorItemModel());
            }
            item.setItemMeta(meta);
            display.setItemStack(item);

            float scale = config.getMeteorModelScale();
            display.setTransformation(new Transformation(
                    new Vector3f(), new Quaternionf(),
                    new Vector3f(scale, scale, scale), new Quaternionf()));
        });
    }

    private void rotateMeteor() {
        modelRotation += config.getMeteorRotationSpeed();
        float radians = (float) Math.toRadians(modelRotation);
        float scale = config.getMeteorModelScale();
        meteorDisplay.setTransformation(new Transformation(
                new Vector3f(), new Quaternionf().rotateXYZ(radians * 0.35f, radians, radians * 0.15f),
                new Vector3f(scale, scale, scale), new Quaternionf()));
    }

    private void handleImpact() {
        if (meteorTarget == null || !hasLanded.compareAndSet(false, true)) return;
        FoliaScheduler.runRegion(plugin, meteorTarget, this::applyImpact);
    }

    private void applyImpact() {
        if (cancelled) return;
        World world = meteorTarget.getWorld();
        if (world != null) {
            List<Player> audience = getNearbyPlayers(meteorTarget, 48);
            if (!audience.isEmpty()) {
                particleService.spawnExplosion(meteorTarget, Particle.EXPLOSION, 5, 20, 0.1, audience);
                particleService.spawnRing(meteorTarget, Particle.SMOKE, 0, 8, 30, 0.1, audience);
            }

            soundService.play("meteor_impact", meteorTarget, audience);

            if (config.getMeteorTerrainDestruction()) {
                world.createExplosion(meteorTarget, 2.0f, false, false);
            } else {
                world.createExplosion(meteorTarget, 0.0f, false, false);
            }

            for (Player player : world.getPlayers()) {
                double distance = player.getLocation().distance(meteorTarget);
                if (distance <= 20) {
                    if (rewardedPlayers.add(player.getUniqueId())) {
                        rewardService.giveRewards(id, SkyEventType.METEOR, player.getUniqueId(), serverId);
                    }
                }
            }

            // Terrain edits are intentionally excluded from the visual impact.
        }

        notificationService.notifyEventPhase(this, "impact");
        cleanupMeteor();
    }

    private List<Player> getNearbyPlayers(Location center, double radius) {
        if (center == null || center.getWorld() == null) return List.of();
        double radiusSq = radius * radius;
        List<Player> result = new ArrayList<>();
        for (Player player : center.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= radiusSq) {
                result.add(player);
            }
        }
        return result;
    }

    private void cleanupMeteor() {
        if (meteorDisplay != null) {
            ItemDisplay display = meteorDisplay;
            FoliaScheduler.runEntity(plugin, display, display::remove);
        }
        meteorDisplay = null;
        if (meteorTask != null) meteorTask.cancel();
        if (trailTask != null) trailTask.cancel();
    }

    @Override
    public void tick(long elapsedSeconds) {
        super.tick(elapsedSeconds);
    }

    @Override
    public void stop() {
        cleanupMeteor();
    }

    @Override
    public void cancel() {
        super.cancel();
        stop();
    }

    @Override
    protected Map<String, Object> getExtraData() {
        return Map.of(
            "targetX", meteorTarget != null ? meteorTarget.getBlockX() : 0,
            "targetY", meteorTarget != null ? meteorTarget.getBlockY() : 0,
            "targetZ", meteorTarget != null ? meteorTarget.getBlockZ() : 0,
            "direction", direction
        );
    }
}
