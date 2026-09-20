package com.valorsky.skysignals.animation;

import com.valorsky.skysignals.util.FoliaScheduler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

public record BlockEffectStep(
    Location offset,
    Material material,
    int durationTicks,
    java.util.function.Consumer<Block> onPlace,
    java.util.function.Consumer<Block> onRemove
) implements AnimationStep {

    public BlockEffectStep(Location offset, Material material, int durationTicks) {
        this(offset, material, durationTicks, null, null);
    }

    @Override
    public void execute(AnimationContext context) {
        Location loc = context.getOrigin().clone().add(offset);
        FoliaScheduler.runRegion(context.getSequence().getPlugin(), loc, () -> {
        Block block = loc.getBlock();
        BlockData originalData = block.getBlockData();
        block.setType(material);
        if (onPlace != null) onPlace.accept(block);

        if (durationTicks > 0) {
            FoliaScheduler.runRegionDelayed(
                context.getSequence().getPlugin(),
                loc,
                () -> {
                    if (block.getType() == material) {
                        block.setBlockData(originalData);
                        if (onRemove != null) onRemove.accept(block);
                    }
                }, durationTicks
            );
        }
        });
    }
}