package com.valorsky.skysignals.event.impl;

import com.valorsky.skysignals.event.AbstractSkyEvent;
import com.valorsky.skysignals.model.EventState;
import com.valorsky.skysignals.model.SkyEventStatus;
import com.valorsky.skysignals.notification.NotificationService;
import com.valorsky.skysignals.util.PositionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.logging.Logger;

public final class MineralRainEvent extends AbstractSkyEvent {

    private final NotificationService notificationService;
    private final Logger logger;
    private transient BukkitTask rainTask;
    private Location center;
    private World world;
    private List<DropEntry> weightedDrops;
    private int maxDrops;
    private int droppedCount = 0;

    public MineralRainEvent(EventState state, JavaPlugin plugin, NotificationService notificationService) {
        super(state, plugin);
        this.notificationService = notificationService;
        this.logger = plugin.getLogger();
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

        loadDrops();

        notificationService.notifyEventStart(this);

        rainTask = new BukkitRunnable() {
            @Override
            public void run() {
                dropMineral();
            }
        }.runTaskTimer(plugin, 10L, 5L);

        logger.info("Mineral rain started at " + center.getWorld().getName());
    }

    private void loadDrops() {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection drops = config.getConfigurationSection("mineral-rain.drops");
        weightedDrops = new ArrayList<>();
        if (drops != null) {
            for (String key : drops.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null) continue;
                int weight = config.getInt("mineral-rain.drops." + key + ".weight", 1);
                weightedDrops.add(new DropEntry(material, weight));
            }
        }
        if (weightedDrops.isEmpty()) {
            weightedDrops.add(new DropEntry(Material.IRON_INGOT, 10));
            weightedDrops.add(new DropEntry(Material.COAL, 20));
        }
        maxDrops = config.getInt("mineral-rain.max-drops", 50);
    }

    private void dropMineral() {
        if (weightedDrops.isEmpty()) return;
        if (droppedCount >= maxDrops) return;

        Material material = pickWeightedDrop();
        if (material == null) return;

        Random random = new Random();
        double offsetX = (random.nextDouble() - 0.5) * 20;
        double offsetZ = (random.nextDouble() - 0.5) * 20;
        Location dropLoc = center.clone().add(offsetX, 20, offsetZ);
        dropLoc.setY(world.getHighestBlockAt(dropLoc.getBlockX(), dropLoc.getBlockZ()).getY() + 2);

        Item dropped = world.dropItem(dropLoc, new ItemStack(material, 1));
        dropped.setVelocity(new org.bukkit.util.Vector(
                (random.nextDouble() - 0.5) * 0.2,
                -0.5,
                (random.nextDouble() - 0.5) * 0.2
        ));

        world.spawnParticle(org.bukkit.Particle.FIREWORK, dropLoc, 5);

        droppedCount++;
    }

    private Material pickWeightedDrop() {
        Random random = new Random();
        int totalWeight = weightedDrops.stream().mapToInt(d -> d.weight()).sum();
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (DropEntry entry : weightedDrops) {
            cumulative += entry.weight();
            if (roll < cumulative) {
                return entry.material();
            }
        }
        return weightedDrops.get(weightedDrops.size() - 1).material();
    }

    @Override
    public void tick() {
    }

    @Override
    public void stop() {
        if (rainTask != null) rainTask.cancel();
    }

    @Override
    public void cancel() {
        super.cancel();
        stop();
    }

    private record DropEntry(Material material, int weight) {}
}
