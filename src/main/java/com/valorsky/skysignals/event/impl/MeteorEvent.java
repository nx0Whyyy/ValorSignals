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
    private String direction;

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
        World world = getSpawnWorld();
        if (world == null) {
            logger.warning("No suitable world for meteor event. Cancelling.");
            this.status = SkyEventStatus.CANCELLED;
            return;
        }

        meteorTarget = findTargetPosition(world);
        if (meteorTarget == null) {
            logger.warning("Could not find safe position for meteor. Cancelling.");
            this.status = SkyEventStatus.CANCELLED;
            return;
        }

        Player firstPlayer = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        direction = PositionUtils.getDirection(firstPlayer, meteorTarget);

        for (Player player : Bukkit.getOnlinePlayers()) {
            notifiedPlayers.add(player.getUniqueId());
        }

        notificationService.notifyEventPhase(this, "warning");
        soundService.playGlobal("meteor_start");
    }

    private void startMeteorDescent() {
        if (meteorTarget == null) return;

        FoliaScheduler.runRegion(plugin, meteorTarget, () -> {
            meteorStart = meteorTarget.clone().add(0, 80, 0);
            meteorBlock = meteorStart.getWorld().spawnFallingBlock(
                meteorStart,
                Material.OBSIDIAN.createBlockData()
            );
            meteorBlock.setDropItem(false);

            Vector velocity = meteorTarget.toVector().subtract(meteorStart.toVector()).normalize().multiply(1.5);
            meteorBlock.setVelocity(velocity);

            soundService.play("meteor_warning", meteorStart, getNearbyPlayers(meteorStart, 48));

            trailTask = FoliaScheduler.runRegionTimer(plugin, meteorStart, () -> {
                if (meteorBlock == null || !meteorBlock.isValid() || hasLanded.get()) {
                    if (trailTask != null) trailTask.cancel();
                    return;
                }
                Location loc = meteorBlock.getLocation();
                List<Player> audience = getNearbyPlayers(loc, 48);
                if (!audience.isEmpty()) {
                    MeteorTrailShape trail = new MeteorTrailShape(meteorStart, loc, 10, 1.5);
                    trail.spawn(loc, Particle.FLAME, 5, 0.01, audience);
                }
            }, 5L, 2L);

            meteorTask = FoliaScheduler.runRegionTimer(plugin, meteorStart, () -> {
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
        if (!hasLanded.compareAndSet(false, true)) return;

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

            for (Player player : Bukkit.getOnlinePlayers()) {
                double distance = player.getLocation().distance(meteorTarget);
                if (distance <= 20) {
                    if (rewardedPlayers.add(player.getUniqueId())) {
                        rewardService.giveRewards(id, SkyEventType.METEOR, player.getUniqueId(), serverId);
                    }
                }
            }

            createCraterEffect();
        }

        cleanupMeteor();
    }

    private void createCraterEffect() {
        if (meteorTarget == null || meteorTarget.getWorld() == null) return;

        World world = meteorTarget.getWorld();
        List<Block> craterBlocks = new ArrayList<>();

        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist <= 3) {
                    Location loc = meteorTarget.clone().add(x, 0, z);
                    loc.setY(world.getHighestBlockYAt(loc));
                    Block block = loc.getBlock();
                    if (block.getType() != Material.AIR) {
                        craterBlocks.add(block);
                        block.setType(Material.AIR);
                    }
                }
            }
        }

        FoliaScheduler.runGlobalDelayed(plugin, () -> {
            for (Block block : craterBlocks) {
                if (block.getType() == Material.AIR) {
                    block.setType(Material.STONE);
                }
            }
        }, 600L);
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

    private World getSpawnWorld() {
        return Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.getWorld().getEnvironment() == World.Environment.NORMAL)
            .map(Player::getWorld)
            .findFirst()
            .orElse(Bukkit.getWorld("world"));
    }

    private Location findTargetPosition(World world) {
        List<Player> players = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(world)) players.add(p);
        }
        if (players.isEmpty()) return null;

        Player player = players.get(random.nextInt(players.size()));
        Location island = PositionUtils.findIslandCenter(player);
        if (island == null) {
            island = player.getLocation().add(20, 50, 20);
        }

        for (int attempt = 0; attempt < 10; attempt++) {
            int offsetX = random.nextInt(30) - 15;
            int offsetZ = random.nextInt(30) - 15;
            Location candidate = island.clone().add(offsetX, 0, offsetZ);
            candidate.setY(world.getHighestBlockYAt(candidate));
            if (PositionUtils.isSafe(candidate)) {
                return candidate.add(0.5, 1, 0.5);
            }
        }
        return island.add(0.5, 1, 0.5);
    }

    private boolean isOnGround(Location location) {
        return location.getBlock().getType() != Material.AIR
            || location.clone().subtract(0, 0.1, 0).getBlock().getType() != Material.AIR;
    }

    private void cleanupMeteor() {
        if (meteorBlock != null && meteorBlock.isValid()) {
            meteorBlock.remove();
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
