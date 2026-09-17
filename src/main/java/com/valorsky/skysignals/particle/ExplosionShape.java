package com.valorsky.skysignals.particle;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.List;

public class ExplosionShape implements ParticleShape {

    private final double radius;
    private final int rings;
    private final int pointsPerRing;

    public ExplosionShape(double radius) {
        this(radius, 3, 16);
    }

    public ExplosionShape(double radius, int rings, int pointsPerRing) {
        this.radius = radius;
        this.rings = Math.max(1, rings);
        this.pointsPerRing = Math.max(4, pointsPerRing);
    }

    @Override
    public void spawn(Location center, Particle particle, int count, double speed, List<Player> audience) {
        if (audience.isEmpty()) return;

        int totalPoints = rings * pointsPerRing;
        int particlesPerPoint = Math.max(1, count / totalPoints);

        for (int r = 1; r <= rings; r++) {
            double currentRadius = radius * r / rings;
            for (int i = 0; i < pointsPerRing; i++) {
                double angle = 2 * Math.PI * i / pointsPerRing;
                double x = center.getX() + currentRadius * Math.cos(angle);
                double z = center.getZ() + currentRadius * Math.sin(angle);
                double y = center.getY() + (Math.random() - 0.5) * radius * 0.5;
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
        return "explosion";
    }
}