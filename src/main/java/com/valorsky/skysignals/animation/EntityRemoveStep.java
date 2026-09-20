package com.valorsky.skysignals.animation;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public record EntityRemoveStep(
    Entity entity
) implements AnimationStep {

    @Override
    public void execute(AnimationContext context) {
        if (entity != null && entity.isValid()) {
            com.valorsky.skysignals.util.FoliaScheduler.runEntity(context.getSequence().getPlugin(), entity, entity::remove);
        }
    }
}