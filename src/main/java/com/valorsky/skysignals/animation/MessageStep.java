package com.valorsky.skysignals.animation;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public record MessageStep(
    Component message
) implements AnimationStep {

    @Override
    public void execute(AnimationContext context) {
        for (Player player : context.getAudience()) {
            player.sendMessage(message);
        }
    }
}