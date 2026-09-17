package com.valorsky.skysignals.particle;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.List;

public class RingShape implements ParticleShape {

    private final double innerRadius;
    private final double outerRadius;
    private final int points;

    public RingShape(double innerRadius, double outerRadius) {
        this(innerRadius, outerRadius, 24);
    }

    public RingShape(double innerRadius, double outerRadius, int points) {
        this.innerRadius = innerRadius;
        this.outerRadius = outerRadius;
        this.points = Math.max(8, points);
    }

    @Override
    public void spawn(Location center, Particle particle, int count, double speed, List<Player> audience) {
        if (audience.isEmpty()) return;

        int particlesPerPoint = Math.max(1, count / (points * 2));
        for (int ring = 0; ring < 2; ring++) {
            double radius = ring == 0 ? innerRadius : outerRadius;
            for (int i = 0; i < points; i++) {
                double angle = 2 * Math.PI * i / points;
                double x = center.getX() + radius * Math.cos(angle);
                double z = center.getZ() + radius * Math.sin(angle);
                Location loc = new Location(center.getWorld(), x, center.getY(), z);

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
        return "ring";
    }
}