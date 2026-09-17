package com.valorsky.skysignals.particle;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.List;

public class CircleShape implements ParticleShape {

    private final double radius;
    private final int points;
    private final double yOffset;

    public CircleShape(double radius) {
        this(radius, 16, 0);
    }

    public CircleShape(double radius, int points) {
        this(radius, points, 0);
    }

    public CircleShape(double radius, int points, double yOffset) {
        this.radius = radius;
        this.points = Math.max(4, points);
        this.yOffset = yOffset;
    }

    @Override
    public void spawn(Location center, Particle particle, int count, double speed, List<Player> audience) {
        if (audience.isEmpty()) return;

        int particlesPerPoint = Math.max(1, count / points);
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            Location loc = new Location(center.getWorld(), x, center.getY() + yOffset, z);

            for (Player player : audience) {
                if (player.getWorld().equals(loc.getWorld()) &&
                    player.getLocation().distanceSquared(loc) <= 2304) {
                    player.spawnParticle(particle, loc, particlesPerPoint, 0, 0, 0, speed);
                }
            }
        }
    }

    @Override
    public String getName() {
        return "circle";
    }
}