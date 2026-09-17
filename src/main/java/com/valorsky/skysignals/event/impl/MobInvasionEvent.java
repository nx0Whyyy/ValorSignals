package com.valorsky.skysignals.event.impl;

import com.valorsky.skysignals.event.AbstractSkyEvent;
import com.valorsky.skysignals.model.EventState;
import com.valorsky.skysignals.model.SkyEventStatus;
import com.valorsky.skysignals.notification.NotificationService;
import com.valorsky.skysignals.util.PositionUtils;
import com.valorsky.skysignals.util.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

public final class MobInvasionEvent extends AbstractSkyEvent {

    private final NamespacedKey spawnTagKey;
    private final NotificationService notificationService;
    private final Logger logger;
    private final Set<Entity> spawnedMobs = new HashSet<>();
    private transient boolean waveScheduled = false;
    private int currentWave = 0;
    private int maxWaves;
    private Location center;
    private World world;

    public MobInvasionEvent(EventState state, JavaPlugin plugin, NotificationService notificationService) {
        super(state, plugin);
        this.notificationService = notificationService;
        this.logger = plugin.getLogger();
        this.spawnTagKey = new NamespacedKey(plugin, "skysignals_spawn");
    }

    public NamespacedKey getSpawnTagKey() {
        return spawnTagKey;
    }

    @Override
    public void start() {
        this.status = SkyEventStatus.ACTIVE;

        world = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().getEnvironment() == World.Environment.NORMAL)
                .map(Player::getWorld)
                .findFirst()
                .orElse(Bukkit.getWorld("world"));

        if (world == null) {
            this.status = SkyEventStatus.CANCELLED;
            return;
        }

        Player target = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().equals(world))
                .findFirst()
                .orElse(null);
        if (target == null) {
            this.status = SkyEventStatus.CANCELLED;
            return;
        }
        center = PositionUtils.findIslandCenter(target);
        if (center == null) center = target.getLocation();

        FileConfiguration config = plugin.getConfig();
        maxWaves = config.getInt("mob-invasion.waves", 3);

        notificationService.notifyEventStart(this);

        FoliaScheduler.runRegion(plugin, center, this::spawnWave);
    }

    private void spawnWave() {
        currentWave++;
        if (currentWave > maxWaves) {
            logger.info("Mob invasion completed after " + currentWave + " waves.");
            return;
        }

        FileConfiguration config = plugin.getConfig();
        String basePath = "mob-invasion.mobs";
        ConfigurationSection section = config.getConfigurationSection(basePath);
        if (section == null) {
            logger.warning("No mob configuration found for mob invasion.");
            return;
        }

        Random random = new Random();
        for (String key : section.getKeys(false)) {
            EntityType type;
            try {
                type = EntityType.valueOf(key.toUpperCase());
            } catch (IllegalArgumentException e) {
                continue;
            }
            int count = config.getInt(basePath + "." + key, 1);
            for (int i = 0; i < count; i++) {
                spawnMob(type, random);
            }
        }

        FoliaScheduler.runRegionDelayed(plugin, center, () -> {
            if (waveScheduled && currentWave < maxWaves) {
                spawnWave();
            }
        }, 300L);
        waveScheduled = true;
    }

    private void spawnMob(EntityType type, Random random) {
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = 8 + random.nextDouble() * 12;
        double x = center.getX() + Math.cos(angle) * distance;
        double z = center.getZ() + Math.sin(angle) * distance;
        Location spawnLoc = new Location(world, x, center.getY(), z);

        Entity entity = world.spawnEntity(spawnLoc, type);
        if (entity instanceof LivingEntity living) {
            living.getPersistentDataContainer().set(spawnTagKey, PersistentDataType.BYTE, (byte) 1);
            spawnedMobs.add(entity);
        }
    }

    @Override
    public void tick() {
        spawnedMobs.removeIf(entity -> !entity.isValid());
    }

    @Override
    public void stop() {
        waveScheduled = false;
        for (Entity entity : spawnedMobs) {
            if (entity instanceof LivingEntity) {
                entity.remove();
            }
        }
        spawnedMobs.clear();
    }

    @Override
    public void cancel() {
        super.cancel();
        stop();
    }

    public Set<Entity> getSpawnedMobs() {
        return spawnedMobs;
    }
}
