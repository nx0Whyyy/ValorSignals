package com.valorsky.skysignals.event.impl;

import com.valorsky.skysignals.event.AbstractSkyEvent;
import com.valorsky.skysignals.model.EventState;
import com.valorsky.skysignals.model.SkyEventStatus;
import com.valorsky.skysignals.model.SkyEventType;
import com.valorsky.skysignals.notification.NotificationService;
import com.valorsky.skysignals.reward.RewardService;
import com.valorsky.skysignals.util.PositionUtils;
import org.bukkit.*;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public final class MeteorEvent extends AbstractSkyEvent {

    private final NotificationService notificationService;
    private final RewardService rewardService;
    private final Logger logger;

    private Location meteorStart;
    private Location meteorTarget;
    private transient FallingBlock meteorBlock;
    private transient BukkitTask meteorTask;
    private boolean hasLanded = false;
    private final Set<UUID> notifiedPlayers = new HashSet<>();
    private final Set<UUID> rewardedPlayers = new HashSet<>();

    public MeteorEvent(EventState state, JavaPlugin plugin, NotificationService notificationService, RewardService rewardService) {
        super(state, plugin);
        this.notificationService = notificationService;
        this.rewardService = rewardService;
        this.logger = plugin.getLogger();
    }

    @Override
    public void start() {
        this.status = SkyEventStatus.ACTIVE;

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

        meteorStart = meteorTarget.clone().add(0, 80, 0);
        meteorBlock = world.spawnFallingBlock(
                meteorStart,
                Material.OBSIDIAN.createBlockData()
        );
        meteorBlock.setDropItem(false);

        Vector velocity = meteorTarget.toVector().subtract(meteorStart.toVector()).normalize().multiply(1.5);
        meteorBlock.setVelocity(velocity);

        notifyDirection();

        meteorTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (meteorBlock == null || !meteorBlock.isValid()) {
                    handleImpact();
                    cancel();
                    return;
                }
                Location loc = meteorBlock.getLocation();
                loc.getWorld().spawnParticle(Particle.FLAME, loc, 5, 0.2, 0.2, 0.2, 0.01);

                if (loc.distance(meteorTarget) < 2.0 || isOnGround(loc)) {
                    handleImpact();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 5L, 2L);

        logger.info("Meteor event started at " + meteorStart.getWorld().getName());
    }

    private void notifyDirection() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String direction = PositionUtils.getDirection(player, meteorTarget);
            notifiedPlayers.add(player.getUniqueId());
        }
        notificationService.notifyEventStart(this);
    }

    private void handleImpact() {
        if (hasLanded) return;
        hasLanded = true;

        World world = meteorTarget.getWorld();
        if (world != null) {
            world.createExplosion(meteorTarget, 2.0f, false, false);

            for (Player player : Bukkit.getOnlinePlayers()) {
                double distance = player.getLocation().distance(meteorTarget);
                if (distance <= 20) {
                    if (rewardedPlayers.add(player.getUniqueId())) {
                        rewardService.giveRewards(player, SkyEventType.METEOR);
                    }
                }
            }
        }

        cleanupMeteor();
    }

    private World getSpawnWorld() {
        Player closest = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().getEnvironment() == World.Environment.NORMAL)
                .findFirst()
                .orElse(null);
        if (closest == null) {
            return Bukkit.getWorld("world");
        }
        return closest.getWorld();
    }

    private Location findTargetPosition(World world) {
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().equals(world))
                .map(p -> (Player) p)
                .toList();
        if (players.isEmpty()) return null;

        Player player = players.get((int) (Math.random() * players.size()));
        Location island = PositionUtils.findIslandCenter(player);
        if (island == null) {
            island = player.getLocation().add(20, 50, 20);
        }

        for (int attempt = 0; attempt < 10; attempt++) {
            int offsetX = (int) (Math.random() * 30) - 15;
            int offsetZ = (int) (Math.random() * 30) - 15;
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
    }

    @Override
    public void tick() {
    }

    @Override
    public void stop() {
        if (meteorTask != null) {
            meteorTask.cancel();
        }
        cleanupMeteor();
    }

    @Override
    public void cancel() {
        super.cancel();
        stop();
    }
}
