package com.valorsky.skysignals.event.impl;

import com.valorsky.skysignals.event.AbstractSkyEvent;
import com.valorsky.skysignals.model.*;
import com.valorsky.skysignals.notification.NotificationService;
import com.valorsky.skysignals.particle.ParticleService;
import com.valorsky.skysignals.particle.MeteorTrailShape;
import com.valorsky.skysignals.reward.RewardService;
import com.valorsky.skysignals.sound.SoundService;
import com.valorsky.skysignals.util.FoliaScheduler;
import com.valorsky.skysignals.util.FoliaScheduler.TaskHandle;
import com.valorsky.skysignals.util.PositionUtils;
import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.location.SafeLocationService;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

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
    private Location meteorTarget;
    private FallingBlock meteorBlock;
    private TaskHandle meteorTask;
    private TaskHandle trailTask;
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

                notificationService.notifyEventPhase(this, "warning");
                soundService.playGlobal("meteor_start");
            });
        });
    }

    @Override
    public boolean isReady() { return meteorTarget != null; }

    private void startMeteorDescent() {
        if (meteorTarget == null) return;

        FoliaScheduler.runRegion(plugin, meteorTarget, () -> {
            if (cancelled || getStatus() == SkyEventStatus.FINISHED) return;
            meteorStart = meteorTarget.clone().add(0, 80, 0);
            meteorBlock = meteorStart.getWorld().spawnFallingBlock(
                meteorStart,
                Material.OBSIDIAN.createBlockData()
            );
            meteorBlock.setPersistent(false);
            meteorBlock.setDropItem(false);
            meteorBlock.setCancelDrop(true);

            Vector velocity = meteorTarget.toVector().subtract(meteorStart.toVector()).normalize().multiply(1.5);
            meteorBlock.setVelocity(velocity);

            soundService.play("meteor_warning", meteorStart, getNearbyPlayers(meteorStart, 48));

            trailTask = FoliaScheduler.runEntityRepeating(plugin, meteorBlock, () -> {
                if (meteorBlock == null || !meteorBlock.isValid() || hasLanded.get()) {
                    if (trailTask != null) trailTask.cancel();
                    return;
                }
                Location loc = meteorBlock.getLocation();
                List<Player> audience = getNearbyPlayers(loc, 48);
                if (!audience.isEmpty()) {
                    MeteorTrailShape trail = new MeteorTrailShape(meteorStart, loc, 10, 1.5);
                    particleService.spawnMeteorTrail(meteorStart, loc, Particle.FLAME, 10, 0.01, audience);
                }
            }, 5L, 2L);

            meteorTask = FoliaScheduler.runEntityRepeating(plugin, meteorBlock, () -> {
                if (meteorBlock == null || !meteorBlock.isValid()) {
                    handleImpact();
                    return;
                }
                Location loc = meteorBlock.getLocation();
                List<Player> audience = getNearbyPlayers(loc, 48);
                if (!audience.isEmpty()) {
                    particleService.spawnCircle(loc, Particle.FLAME, 2, 5, 0.01, audience);
                }

                if (loc.distance(meteorTarget) < 2.0 || isOnGround(loc)) {
                    handleImpact();
                }
            }, 5L, 2L);

            logger.info("Meteor descent started at " + meteorStart.getWorld().getName());
        });
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

    private boolean isOnGround(Location location) {
        return location.getBlock().getType() != Material.AIR
            || location.clone().subtract(0, 0.1, 0).getBlock().getType() != Material.AIR;
    }

    private void cleanupMeteor() {
        if (meteorBlock != null) {
            FallingBlock block = meteorBlock;
            FoliaScheduler.runEntity(plugin, block, block::remove);
        }
        meteorBlock = null;
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
