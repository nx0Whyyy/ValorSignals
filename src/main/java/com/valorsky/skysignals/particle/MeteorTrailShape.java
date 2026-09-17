package com.valorsky.skysignals.particle;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

public class MeteorTrailShape implements ParticleShape {

    private final Location start;
    private final Location end;
    private final int segments;
    private final double spread;

    public MeteorTrailShape(Location start, Location end) {
        this(start, end, 15, 1.5);
    }

    public MeteorTrailShape(Location start, Location end, int segments, double spread) {
        this.start = start;
        this.end = end;
        this.segments = Math.max(5, segments);
        this.spread = spread;
    }

    @Override
    public void spawn(Location center, Particle particle, int count, double speed, List<Player> audience) {
        if (audience.isEmpty()) return;

        Vector direction = end.toVector().subtract(start.toVector());
        double length = direction.length();
        if (length == 0) return;

        direction.normalize();
        int particlesPerSegment = Math.max(1, count / segments);

        for (int s = 0; s < segments; s++) {
            double progress = s / (double) segments;
            Location base = start.clone().add(direction.clone().multiply(length * progress));

            for (int i = 0; i < 3; i++) {
                double angle = Math.random() * 2 * Math.PI;
                double r = spread * Math.random();
                double offsetX = r * Math.cos(angle);
                double offsetZ = r * Math.sin(angle);
                double offsetY = (Math.random() - 0.5) * spread * 0.5;

                Location loc = base.clone().add(offsetX, offsetY, offsetZ);

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
        return "meteor_trail";
    }
}