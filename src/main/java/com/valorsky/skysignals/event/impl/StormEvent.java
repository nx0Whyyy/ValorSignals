package com.valorsky.skysignals.event.impl;

import com.valorsky.skysignals.event.AbstractSkyEvent;
import com.valorsky.skysignals.event.SkyEventManager;
import com.valorsky.skysignals.model.*;
import com.valorsky.skysignals.notification.NotificationService;
import com.valorsky.skysignals.particle.ParticleService;
import com.valorsky.skysignals.particle.CloudShape;
import com.valorsky.skysignals.reward.RewardService;
import com.valorsky.skysignals.sound.SoundService;
import com.valorsky.skysignals.util.FoliaScheduler;
import com.valorsky.skysignals.util.FoliaScheduler.TaskHandle;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public final class StormEvent extends AbstractSkyEvent {

    private final NotificationService notificationService;
    private final RewardService rewardService;
    private final ParticleService particleService;
    private final SoundService soundService;
    private final Logger logger;

    private volatile World world;
    private boolean weatherCaptured;
    private boolean wasStorming = false;
    private boolean wasThundering = false;
    private TaskHandle stormTask;
    private TaskHandle lightningTask;
    private final AtomicInteger lightningCounter = new AtomicInteger(0);

    public StormEvent(EventState state, JavaPlugin plugin, NotificationService notificationService,
                      RewardService rewardService, ParticleService particleService,
                      SoundService soundService) {
        super(state, plugin);
        this.notificationService = notificationService;
        this.rewardService = rewardService;
        this.particleService = particleService;
        this.soundService = soundService;
        this.logger = plugin.getLogger();
    }

    @Override
    public List<EventLocation> getEventLocations() {
        World affected = world;
        return affected == null ? List.of() : List.of(EventLocation.world(affected.getName(), "Orage dans tout le monde"));
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    protected void onAnnouncing() {
        this.status = SkyEventStatus.ANNOUNCING;
        findWorldAndPrepare();
    }

    @Override
    protected void onWarning() {
        this.status = SkyEventStatus.WARNING;
        startTransition();
    }

    @Override
    protected void onActive() {
        this.status = SkyEventStatus.ACTIVE;
        startStorm();
    }

    @Override
    protected void onCompleting() {
        this.status = SkyEventStatus.COMPLETING;
        startDissipation();
    }

    @Override
    protected void onFinished() {
        this.status = SkyEventStatus.FINISHED;
        restoreWeather();
    }

    @Override
    protected void onCancelled() {
        this.status = SkyEventStatus.CANCELLED;
        restoreWeather();
    }

    private void findWorldAndPrepare() {
        world = Bukkit.getOnlinePlayers().stream()
            .filter(p -> p.getWorld().getEnvironment() == World.Environment.NORMAL)
            .map(Player::getWorld)
            .findFirst()
            .orElse(Bukkit.getWorld("world"));

        if (world == null) {
            logger.warning("No normal world found for storm event.");
            this.status = SkyEventStatus.CANCELLED;
            return;
        }

        soundService.playGlobal("storm_start");
    }

    private void startTransition() {
        if (world == null) return;

        weatherCaptured = true;
        wasStorming = world.hasStorm();
        wasThundering = world.isThundering();

        notificationService.notifyEventPhase(this, "transition");
        soundService.play("storm_tick", world.getSpawnLocation(), getNearbyPlayers(world.getSpawnLocation(), 48));

        stormTask = FoliaScheduler.runGlobalTimer(plugin, () -> {
            if (world == null) return;
            List<Player> audience = getNearbyPlayers(world.getSpawnLocation(), 48);
            if (!audience.isEmpty()) {
                CloudShape cloud = new CloudShape(20, 3, 12);
                Location loc = world.getSpawnLocation();
                particleService.spawnCloud(loc, Particle.CLOUD, 20, 10, 0.01, audience);
            }
        }, 20L, 10L);

    }

    private void startStorm() {
        if (world == null) return;

        world.setStorm(true);
        world.setThundering(true);
        world.setWeatherDuration(999999);

        notificationService.notifyEventPhase(this, "active");
        soundService.playGlobal("storm_active");

        lightningTask = FoliaScheduler.runGlobalTimer(plugin, () -> {
            if (world == null) return;

            List<Player> players = world.getPlayers();
            if (players.isEmpty()) return;

            Player target = players.get(random.nextInt(players.size()));
            Location loc = target.getLocation().add(
                random.nextInt(20) - 10, 0, random.nextInt(20) - 10
            );
            FoliaScheduler.runRegion(plugin, loc, () -> {
                if (cancelled || getStatus() == SkyEventStatus.FINISHED) return;
                if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;
                loc.setY(world.getHighestBlockYAt(loc));
                world.strikeLightningEffect(loc);
                for (Player p : players) {
                    if (p.getWorld().equals(world)) {
                        FoliaScheduler.runEntity(plugin, p, () -> p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f));
                    }
                }
            });

            lightningCounter.incrementAndGet();
        }, 100L, 40L);

        logger.info("Storm event started on world " + world.getName());
    }

    private void startDissipation() {
        if (world == null) return;

        if (lightningTask != null) lightningTask.cancel();

        lightningTask = FoliaScheduler.runGlobalTimer(plugin, () -> {
            if (world == null) return;
            List<Player> players = world.getPlayers();
            if (players.isEmpty()) return;

            Player target = players.get(random.nextInt(players.size()));
            Location loc = target.getLocation().add(
                random.nextInt(30) - 15, 0, random.nextInt(30) - 15
            );
            FoliaScheduler.runRegion(plugin, loc, () -> {
                if (cancelled || getStatus() == SkyEventStatus.FINISHED) return;
                if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;
                loc.setY(world.getHighestBlockYAt(loc));
                world.strikeLightningEffect(loc);
            });
        }, 200L, 100L);

        notificationService.notifyEventPhase(this, "finish");
        soundService.playGlobal("storm_finish");

        FoliaScheduler.runGlobalDelayed(plugin, this::restoreWeather, 200L);
    }

    private void restoreWeather() {
        if (stormTask != null) stormTask.cancel();
        if (lightningTask != null) lightningTask.cancel();

        if (world != null && weatherCaptured) {
            weatherCaptured = false;
            Runnable restore = () -> {
                world.setStorm(wasStorming);
                world.setThundering(wasThundering);
                logger.info("Storm event stopped, weather restored.");
            };
            if (Bukkit.isGlobalTickThread()) restore.run();
            else FoliaScheduler.runGlobal(plugin, restore);
        }
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

    @Override
    public void tick(long elapsedSeconds) {
        super.tick(elapsedSeconds);
    }

    @Override
    public void stop() {
        restoreWeather();
    }

    @Override
    public void cancel() {
        super.cancel();
        stop();
    }

    @Override
    protected Map<String, Object> getExtraData() {
        return Map.of(
            "world", world != null ? world.getName() : "none",
            "lightningStrikes", lightningCounter.get(),
            "wasStorming", wasStorming,
            "wasThundering", wasThundering
        );
    }
}