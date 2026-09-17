package com.valorsky.skysignals.animation;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public record EntityMoveStep(
    Entity entity,
    Location targetOffset,
    int durationTicks
) implements AnimationStep {

    @Override
    public void execute(AnimationContext context) {
        if (entity == null || !entity.isValid()) return;
        Location target = context.getOrigin().clone().add(targetOffset);
        entity.teleport(target);
    }
}