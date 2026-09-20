package com.valorsky.skysignals.animation;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public record EntitySpawnStep(
    EntityType entityType,
    Location offset,
    java.util.function.Consumer<Entity> configurator
) implements AnimationStep {

    public EntitySpawnStep(EntityType entityType, Location offset) {
        this(entityType, offset, null);
    }

    @Override
    public void execute(AnimationContext context) {
        Location loc = context.getOrigin().clone().add(offset);
        com.valorsky.skysignals.util.FoliaScheduler.runRegion(context.getSequence().getPlugin(), loc, () -> {
        Entity entity = loc.getWorld().spawnEntity(loc, entityType);
        if (configurator != null) {
            configurator.accept(entity);
        }
        });
    }
}