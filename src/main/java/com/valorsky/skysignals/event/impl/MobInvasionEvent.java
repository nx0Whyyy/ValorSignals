package com.valorsky.skysignals.event.impl;

import com.valorsky.skysignals.event.AbstractSkyEvent;
import com.valorsky.skysignals.event.SkyEventManager;
import com.valorsky.skysignals.model.*;
import com.valorsky.skysignals.notification.NotificationService;
import com.valorsky.skysignals.particle.ParticleService;
import com.valorsky.skysignals.reward.RewardService;
import com.valorsky.skysignals.sound.SoundService;
import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.util.FoliaScheduler;
import com.valorsky.skysignals.util.FoliaScheduler.TaskHandle;
import com.valorsky.skysignals.location.SafeLocationService;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public final class MobInvasionEvent extends AbstractSkyEvent {

    private final NotificationService notificationService;
    private final RewardService rewardService;
    private final ParticleService particleService;
    private final SoundService soundService;
    private final SafeLocationService locationService;
    private final Config config;
    private final Logger logger;

    private Location spawnCenter;
    private final List<LivingEntity> spawnedMobs = new java.util.concurrent.CopyOnWriteArrayList<>();
    private TaskHandle waveTask;
    private TaskHandle checkTask;
    private final AtomicInteger currentWave = new AtomicInteger(0);
    private final AtomicInteger mobsRemaining = new AtomicInteger(0);
    private final Config.MobWaveConfig[] waves;
    private final int maxMobs;
    private final Set<UUID> rewardedPlayers = ConcurrentHashMap.newKeySet();

    public MobInvasionEvent(EventState state, JavaPlugin plugin, NotificationService notificationService,
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
        this.waves = config.getMobInvasionWaves().toArray(new Config.MobWaveConfig[0]);
        this.maxMobs = config.getMobInvasionMaxMobs();
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    protected void onAnnouncing() {
        this.status = SkyEventStatus.ANNOUNCING;
        findSpawnLocation();
    }

    @Override
    protected void onWarning() {
        this.status = SkyEventStatus.WARNING;
        startFirstWave();
    }

    @Override
    protected void onActive() {
        this.status = SkyEventStatus.ACTIVE;
    }

    @Override
    protected void onCompleting() {
        this.status = SkyEventStatus.COMPLETING;
        cleanupMobs();
    }

    @Override
    protected void onFinished() {
        this.status = SkyEventStatus.FINISHED;
    }

    @Override
    protected void onCancelled() {
        this.status = SkyEventStatus.CANCELLED;
        cleanupMobs();
    }

    private void findSpawnLocation() {
        locationService.findNearPlayersAsync(30, 100).whenComplete((location, error) -> {
            if (!plugin.isEnabled()) return;
            FoliaScheduler.runGlobal(plugin, () -> {
                if (getStatus() != SkyEventStatus.ANNOUNCING) return;
                if (error != null || location.isEmpty()) {
                    logger.warning("No safe loaded location for " + getType());
                    cancel();
                    return;
                }
                Location loc = location.get();
                spawnCenter = loc;

                notificationService.notifyEventPhase(this, "start");
                soundService.playGlobal("mob_invasion_start");
            });
        });
    }

    @Override
    public boolean isReady() { return spawnCenter != null; }

    private void startFirstWave() {
        currentWave.set(0);
        spawnWave(0);
    }

    private void spawnWave(int waveIndex) {
        if (cancelled || (getStatus() == SkyEventStatus.FINISHED || getStatus() == SkyEventStatus.COMPLETING)) return;
        if (waveIndex >= waves.length) {
            checkTask = FoliaScheduler.runGlobalTimer(plugin, this::checkMobsRemaining, 20L, 20L);
            return;
        }

        Config.MobWaveConfig wave = waves[waveIndex];
        currentWave.set(waveIndex + 1);
        int totalThisWave = wave.mobs().values().stream().mapToInt(Integer::intValue).sum();


        notificationService.notifyEventPhase(this, "wave");
        soundService.playGlobal("mob_invasion_wave");

        int delay = wave.delay() * 20;
        waveTask = FoliaScheduler.runGlobalDelayed(plugin, () -> {
            if (cancelled || (getStatus() == SkyEventStatus.FINISHED || getStatus() == SkyEventStatus.COMPLETING)) return;
            for (Map.Entry<String, Integer> entry : wave.mobs().entrySet()) {
                String mobType = entry.getKey();
                int count = entry.getValue();
                for (int i = 0; i < count; i++) {
                    spawnMob(mobType);
                }
            }

            spawnWave(waveIndex + 1);
        }, delay);
    }

    private void spawnMob(String mobType) {
        if (spawnCenter == null || spawnCenter.getWorld() == null) return;
        if (spawnedMobs.size() >= maxMobs) return;

        World world = spawnCenter.getWorld();
        EntityType type;
        try { type = EntityType.valueOf(mobType.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { logger.warning("Invalid mob type: " + mobType); return; }
        if (type == null || !type.isAlive()) {
            logger.warning("Invalid mob type: " + mobType);
            return;
        }

        double angle = Math.random() * 2 * Math.PI;
        double distance = 5 + Math.random() * 10;
        Location spawnLoc = spawnCenter.clone().add(
            distance * Math.cos(angle), 0, distance * Math.sin(angle)
        );
        if (mobsRemaining.incrementAndGet() > maxMobs) { mobsRemaining.decrementAndGet(); return; }
        FoliaScheduler.runRegion(plugin, spawnLoc, () -> {
        if (cancelled || (getStatus() == SkyEventStatus.FINISHED || getStatus() == SkyEventStatus.COMPLETING) || !world.isChunkLoaded(spawnLoc.getBlockX() >> 4, spawnLoc.getBlockZ() >> 4)) {
            mobsRemaining.decrementAndGet(); return;
        }
        spawnLoc.setY(world.getHighestBlockYAt(spawnLoc) + 1);

        LivingEntity mob = (LivingEntity) world.spawnEntity(spawnLoc, type);
        mob.setPersistent(false);

        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        pdc.set(new NamespacedKey(plugin, "skysignals_event"), PersistentDataType.STRING, id.toString());
        pdc.set(new NamespacedKey(plugin, "skysignals_mob"), PersistentDataType.BYTE, (byte) 1);

        if (mob.getAttribute(Attribute.MAX_HEALTH) != null) {
            mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(mob.getAttribute(Attribute.MAX_HEALTH).getBaseValue() * 1.2);
            mob.setHealth(mob.getAttribute(Attribute.MAX_HEALTH).getBaseValue());
        }
        mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, false, false));

        spawnedMobs.add(mob);

        List<Player> audience = getNearbyPlayers(spawnLoc, 48);
        if (!audience.isEmpty()) {
            particleService.spawnCircle(spawnLoc, Particle.SOUL_FIRE_FLAME, 2, 10, 0.1, audience);
        }
        });
    }

    private void checkMobsRemaining() {
        spawnedMobs.removeIf(mob -> !mob.isValid() || mob.isDead());

        int alive = spawnedMobs.size();
        mobsRemaining.set(alive);

        notificationService.notifyEventPhase(this, "remaining");

        if (alive == 0) {
            if (checkTask != null) checkTask.cancel();
            FoliaScheduler.runGlobalDelayed(plugin, () -> {
                if (!cancelled && getStatus() != SkyEventStatus.FINISHED)
                    ((com.valorsky.skysignals.SkySignalsPlugin) plugin).getApi().getEventManager().transitionPhase(this, SkyEventPhase.COMPLETING);
            }, 20L);
        }
    }

    public void onMobKill(Player killer, LivingEntity mob) {
        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        String eventId = pdc.get(new NamespacedKey(plugin, "skysignals_event"), PersistentDataType.STRING);
        if (eventId != null && eventId.equals(this.id.toString())) {
            if (rewardedPlayers.add(killer.getUniqueId())) {
                rewardService.giveRewards(id, SkyEventType.MOB_INVASION, killer.getUniqueId(), serverId);
            }
        }
    }

    private void cleanupMobs() {
        for (LivingEntity mob : spawnedMobs) {
            if (mob != null) FoliaScheduler.runEntity(plugin, mob, mob::remove);
        }
        spawnedMobs.clear();
        if (waveTask != null) waveTask.cancel();
        if (checkTask != null) checkTask.cancel();
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
        cleanupMobs();
    }

    @Override
    public void cancel() {
        super.cancel();
        stop();
    }

    @Override
    protected Map<String, Object> getExtraData() {
        return Map.of(
            "spawnX", spawnCenter != null ? spawnCenter.getBlockX() : 0,
            "spawnY", spawnCenter != null ? spawnCenter.getBlockY() : 0,
            "spawnZ", spawnCenter != null ? spawnCenter.getBlockZ() : 0,
            "currentWave", currentWave.get(),
            "totalWaves", waves.length,
            "mobsRemaining", mobsRemaining.get(),
            "mobsSpawned", spawnedMobs.size()
        );
    }
}