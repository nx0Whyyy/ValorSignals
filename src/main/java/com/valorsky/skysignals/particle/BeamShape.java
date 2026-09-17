package com.valorsky.skysignals.particle;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

public class BeamShape implements ParticleShape {

    private final Location start;
    private final Location end;
    private final double radius;
    private final int segments;

    public BeamShape(Location start, Location end) {
        this(start, end, 0.5, 10);
    }

    public BeamShape(Location start, Location end, double radius, int segments) {
        this.start = start;
        this.end = end;
        this.radius = radius;
        this.segments = Math.max(1, segments);
    }

    @Override
    public void spawn(Location center, Particle particle, int count, double speed, List<Player> audience) {
        if (audience.isEmpty()) return;

        Vector direction = end.toVector().subtract(start.toVector());
        double length = direction.length();
        if (length == 0) return;

        direction.normalize();
        Vector perpendicular1 = new Vector(-direction.getZ(), 0, direction.getX()).normalize();
        if (perpendicular1.length() == 0) {
            perpendicular1 = new Vector(0, 1, 0);
        }
        Vector perpendicular2 = direction.clone().crossProduct(perpendicular1).normalize();

        int particlesPerSegment = Math.max(1, count / segments);

        for (int s = 0; s < segments; s++) {
            double progress = (s + 0.5) / segments;
            Location base = start.clone().add(direction.clone().multiply(length * progress));

            for (int i = 0; i < 4; i++) {
                double angle = 2 * Math.PI * i / 4;
                double offsetX = radius * Math.cos(angle);
                double offsetZ = radius * Math.sin(angle);
                Vector offset = perpendicular1.clone().multiply(offsetX).add(perpendicular2.clone().multiply(offsetZ));
                Location loc = base.clone().add(offset);

                for (Player player : audience) {
                    if (player.getWorld().equals(loc.getWorld()) &&
                        player.getLocation().distanceSquared(loc) <= 2304) {
                        player.spawnParticle(particle, loc, particlesPerSegment, 0, 0, 0, speed);
                    }
                }
            }
        }
    }

    @Override
    public String getName() {
        return "beam";
    }
}