package com.valorsky.skysignals.animation;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public record SoundStep(
    Sound sound,
    Location offset,
    float volume,
    float pitch
) implements AnimationStep {

    public SoundStep(Sound sound) {
        this(sound, null, 1.0f, 1.0f);
    }

    @Override
    public void execute(AnimationContext context) {
        Location origin = context.getOrigin();
        for (Player player : context.getAudience()) {
            if (player.getWorld().equals(origin.getWorld()) &&
                player.getLocation().distanceSquared(origin) <= 2304) {
                Location loc = offset != null ? origin.clone().add(offset) : origin.clone();
                player.playSound(loc, sound, volume, pitch);
            }
        }
    }
}