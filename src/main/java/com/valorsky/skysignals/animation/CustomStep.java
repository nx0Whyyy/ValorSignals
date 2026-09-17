package com.valorsky.skysignals.animation;

import org.bukkit.entity.Player;

public record CustomStep(
    java.util.function.Consumer<AnimationContext> action
) implements AnimationStep {

    @Override
    public void execute(AnimationContext context) {
        action.accept(context);
    }
}