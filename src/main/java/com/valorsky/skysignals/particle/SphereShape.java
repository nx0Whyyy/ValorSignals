package com.valorsky.skysignals.particle;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.List;

public class SphereShape implements ParticleShape {

    private final double radius;
    private final int horizontalPoints;
    private final int verticalPoints;

    public SphereShape(double radius) {
        this(radius, 12, 8);
    }

    public SphereShape(double radius, int horizontalPoints, int verticalPoints) {
        this.radius = radius;
        this.horizontalPoints = Math.max(4, horizontalPoints);
        this.verticalPoints = Math.max(2, verticalPoints);
    }

    @Override
    public void spawn(Location center, Particle particle, int count, double speed, List<Player> audience) {
        if (audience.isEmpty()) return;

        int totalPoints = horizontalPoints * verticalPoints;
        int particlesPerPoint = Math.max(1, count / totalPoints);

        for (int v = 0; v < verticalPoints; v++) {
            double phi = Math.PI * (v + 1) / (verticalPoints + 1);
            double y = center.getY() + radius * Math.cos(phi);
            double r = radius * Math.sin(phi);

            for (int h = 0; h < horizontalPoints; h++) {
                double theta = 2 * Math.PI * h / horizontalPoints;
                double x = center.getX() + r * Math.cos(theta);
                double z = center.getZ() + r * Math.sin(theta);
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
        return "sphere";
    }
}