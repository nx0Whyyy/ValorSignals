package com.valorsky.skysignals.animation;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

public record ActionBarStep(
    Component message
) implements AnimationStep {

    @Override
    public void execute(AnimationContext context) {
        for (Player player : context.getAudience()) {
            player.sendActionBar(message);
        }
    }
}