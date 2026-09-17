package com.valorsky.skysignals.particle;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.List;

public class HelixShape implements ParticleShape {

    private final double radius;
    private final double height;
    private final int turns;
    private final int pointsPerTurn;

    public HelixShape(double radius, double height) {
        this(radius, height, 3, 12);
    }

    public HelixShape(double radius, double height, int turns, int pointsPerTurn) {
        this.radius = radius;
        this.height = height;
        this.turns = Math.max(1, turns);
        this.pointsPerTurn = Math.max(4, pointsPerTurn);
    }

    @Override
    public void spawn(Location center, Particle particle, int count, double speed, List<Player> audience) {
        if (audience.isEmpty()) return;

        int totalPoints = turns * pointsPerTurn;
        int particlesPerPoint = Math.max(1, count / totalPoints);

        for (int t = 0; t < turns; t++) {
            for (int i = 0; i < pointsPerTurn; i++) {
                double progress = (t * pointsPerTurn + i) / (double) totalPoints;
                double angle = 2 * Math.PI * (t + i / (double) pointsPerTurn);
                double y = center.getY() + progress * height;

                double x = center.getX() + radius * Math.cos(angle);
                double z = center.getZ() + radius * Math.sin(angle);
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
        return "helix";
    }
}