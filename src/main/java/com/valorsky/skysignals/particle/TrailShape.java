package com.valorsky.skysignals.particle;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

public class TrailShape implements ParticleShape {

    private final Location start;
    private final Location end;
    private final int points;

    public TrailShape(Location start, Location end) {
        this(start, end, 20);
    }

    public TrailShape(Location start, Location end, int points) {
        this.start = start;
        this.end = end;
        this.points = Math.max(2, points);
    }

    @Override
    public void spawn(Location center, Particle particle, int count, double speed, List<Player> audience) {
        if (audience.isEmpty()) return;

        Vector direction = end.toVector().subtract(start.toVector());
        double length = direction.length();
        if (length == 0) return;

        direction.normalize();
        int particlesPerPoint = Math.max(1, count / points);

        for (int i = 0; i < points; i++) {
            double progress = (i + 0.5) / points;
            Location loc = start.clone().add(direction.clone().multiply(length * progress));

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
        return "trail";
    }
}