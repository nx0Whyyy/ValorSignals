package com.valorsky.skysignals.particle;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.List;

public class CloudShape implements ParticleShape {

    private final double radius;
    private final int layers;
    private final int pointsPerLayer;

    public CloudShape(double radius) {
        this(radius, 3, 12);
    }

    public CloudShape(double radius, int layers, int pointsPerLayer) {
        this.radius = radius;
        this.layers = Math.max(1, layers);
        this.pointsPerLayer = Math.max(4, pointsPerLayer);
    }

    @Override
    public void spawn(Location center, Particle particle, int count, double speed, List<Player> audience) {
        if (audience.isEmpty()) return;

        int totalPoints = layers * pointsPerLayer;
        int particlesPerPoint = Math.max(1, count / totalPoints);

        for (int l = 0; l < layers; l++) {
            double layerProgress = l / (double) layers;
            double layerRadius = radius * (1 - layerProgress * 0.5);
            double layerY = center.getY() + layerProgress * radius * 0.5;

            for (int i = 0; i < pointsPerLayer; i++) {
                double angle = 2 * Math.PI * i / pointsPerLayer;
                double jitter = (Math.random() - 0.5) * 0.3;
                double x = center.getX() + layerRadius * Math.cos(angle) * (1 + jitter);
                double z = center.getZ() + layerRadius * Math.sin(angle) * (1 + jitter);
                double y = layerY + (Math.random() - 0.5) * radius * 0.2;
                Location loc = new Location(center.getWorld(), x, y, z);

                for (Player player : audience) {
                    if (player.getWorld().equals(loc.getWorld()) &&
                        player.getLocation().distanceSquared(loc) <= 2304) {
                        player.spawnParticle(particle, loc, particlesPerPoint, 0, 0, 0, speed);
                    }
                }
            }
        }
    }

    @Override
    public String getName() {
        return "cloud";
    }
}