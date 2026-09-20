package com.valorsky.skysignals.particle;

import com.valorsky.skysignals.config.Config;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Logger;

public final class ParticleService {

    private final JavaPlugin plugin;
    private final Config config;
    private final Logger logger;
    private int particlesThisSecond = 0;
    private long lastSecond = System.currentTimeMillis() / 1000;

    public ParticleService(JavaPlugin plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();
    }

    private synchronized boolean reserve(int count) {
        long second = System.currentTimeMillis() / 1000;
        if (second != lastSecond) { lastSecond = second; particlesThisSecond = 0; }
        if (particlesThisSecond + count > config.maxParticlesPerSecond()) return false;
        particlesThisSecond += count;
        return true;
    }

    private void emit(Location location, Particle particle, int count, double speed, List<Player> audience) {
        if (!config.visualsEnabled() || !config.particlesEnabled() || count <= 0) return;
        for (Player player : audience) {
            com.valorsky.skysignals.util.FoliaScheduler.runEntity(plugin, player, () -> {
                if (player.getWorld().equals(location.getWorld()) &&
                        player.getLocation().distanceSquared(location) <= (double) config.viewDistance() * config.viewDistance() && reserve(count)) {
                    player.spawnParticle(particle, location, count, 0, 0, 0, speed);
                }
            });
        }
    }

    public void spawnCircle(Location center, Particle particle, double radius, int count, double speed, List<Player> audience) {
        int points = Math.max(1, (int) (radius * 3));
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location loc = new Location(center.getWorld(), x, center.getY(), z);

            emit(loc, particle, Math.max(1, count / points), speed, audience);
        }
    }

    public void spawnSphere(Location center, Particle particle, double radius, int count, double speed, List<Player> audience) {
        spawnCircle(center, particle, radius, count, speed, audience);
    }

    public void spawnHelix(Location center, Particle particle, double radius, double height, int count, double speed, List<Player> audience) {
        int turns = 3;
        int pointsPerTurn = 12;
        for (int t = 0; t < turns; t++) {
            for (int i = 0; i < pointsPerTurn; i++) {
                double progress = (t * pointsPerTurn + i) / (double) (turns * pointsPerTurn);
                double angle = 2 * Math.PI * (t + i / (double) pointsPerTurn);
                double y = center.getY() + progress * height;
                double x = center.getX() + radius * Math.cos(angle);
                double z = center.getZ() + radius * Math.sin(angle);
                Location loc = new Location(center.getWorld(), x, y, z);

                emit(loc, particle, Math.max(1, count / (turns * pointsPerTurn)), speed, audience);
            }
        }
    }

    public void spawnRing(Location center, Particle particle, double innerRadius, double outerRadius, int count, double speed, List<Player> audience) {
        int points = 24;
        for (int ring = 0; ring < 2; ring++) {
            double radius = ring == 0 ? innerRadius : outerRadius;
            for (int i = 0; i < points; i++) {
                double angle = 2 * Math.PI * i / points;
                double x = center.getX() + radius * Math.cos(angle);
                double z = center.getZ() + radius * Math.sin(angle);
                Location loc = new Location(center.getWorld(), x, center.getY(), z);

                emit(loc, particle, Math.max(1, count / (points * 2)), speed, audience);
            }
        }
    }

    public void spawnTrail(Location start, Location end, Particle particle, int count, double speed, List<Player> audience) {
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double dz = end.getZ() - start.getZ();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length == 0) return;

        int points = Math.max(2, count / 3);
        for (int i = 0; i < points; i++) {
            double progress = (i + 0.5) / points;
            Location loc = start.clone().add(dx * progress, dy * progress, dz * progress);

            emit(loc, particle, Math.max(1, 3), speed, audience);
        }
    }

    public void spawnExplosion(Location center, Particle particle, double radius, int count, double speed, List<Player> audience) {
        spawnRing(center, particle, 0, radius, count, speed, audience);
    }

    public void spawnBeam(Location start, Location end, Particle particle, double radius, int count, double speed, List<Player> audience) {
        spawnTrail(start, end, particle, count, speed, audience);
    }

    public void spawnSpiral(Location center, Particle particle, double startRadius, double endRadius, double height, int count, double speed, List<Player> audience) {
        spawnHelix(center, particle, endRadius, height, count, speed, audience);
    }

    public void spawnMeteorTrail(Location start, Location end, Particle particle, int count, double speed, List<Player> audience) {
        spawnTrail(start, end, particle, count, speed, audience);
    }

    public void spawnCloud(Location center, Particle particle, double radius, int count, double speed, List<Player> audience) {
        spawnSphere(center, particle, radius, count, speed, audience);
    }
}