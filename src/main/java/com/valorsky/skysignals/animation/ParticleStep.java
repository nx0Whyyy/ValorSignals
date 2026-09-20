package com.valorsky.skysignals.animation;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

public record ParticleStep(
    Particle particle,
    Location offset,
    int count,
    double offsetX,
    double offsetY,
    double offsetZ,
    double speed,
    double extra
) implements AnimationStep {

    public ParticleStep(Particle particle) {
        this(particle, null, 1, 0, 0, 0, 0, 0);
    }

    @Override
    public void execute(AnimationContext context) {
        Location origin = context.getOrigin();
        for (Player player : context.getAudience()) {
            if (player.getWorld().equals(origin.getWorld()) &&
                player.getLocation().distanceSquared(origin) <= 2304) { // 48^2
                Location loc = offset != null ? origin.clone().add(offset) : origin.clone();
                player.spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, speed);
            }
        }
    }
}