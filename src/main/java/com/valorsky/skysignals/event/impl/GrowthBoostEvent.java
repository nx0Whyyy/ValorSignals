package com.valorsky.skysignals.event.impl;

import com.valorsky.skysignals.event.AbstractSkyEvent;
import com.valorsky.skysignals.model.EventState;
import com.valorsky.skysignals.model.SkyEventStatus;
import com.valorsky.skysignals.notification.NotificationService;
import com.valorsky.skysignals.util.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public final class GrowthBoostEvent extends AbstractSkyEvent {

    private final NotificationService notificationService;
    private final Logger logger;
    private final Set<Block> boostedBlocks = new HashSet<>();
    private double multiplier;
    private long tickCounter = 0;

    public GrowthBoostEvent(EventState state, JavaPlugin plugin, NotificationService notificationService) {
        super(state, plugin);
        this.notificationService = notificationService;
        this.logger = plugin.getLogger();
    }

    @Override
    public void start() {
        this.status = SkyEventStatus.ACTIVE;

        multiplier = plugin.getConfig().getDouble("growth-boost.multiplier", 2.0);

        notificationService.notifyEventStart(this);
        logger.info("Growth boost started with multiplier " + multiplier);
    }

    @Override
    public void tick() {
        tickCounter++;
        if (tickCounter % 40 == 0) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                FoliaScheduler.runEntity(plugin, player, () -> boostNearbyCrops(player));
            }
        }
    }

    private void boostNearbyCrops(Player player) {
        Location center = player.getLocation();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                Block block = center.getWorld().getBlockAt(center.getBlockX() + x, center.getBlockY(), center.getBlockZ() + z);
                BlockState state = block.getState();
                if (state.getBlockData() instanceof Ageable ageable) {
                    if (Math.random() < (0.15 * multiplier)) {
                        int currentAge = ageable.getAge();
                        int maxAge = ageable.getMaximumAge();
                        if (currentAge < maxAge) {
                            ageable.setAge(Math.min(maxAge, currentAge + 1));
                            state.update();
                            boostedBlocks.add(block);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void stop() {
        boostedBlocks.clear();
        logger.info("Growth boost ended.");
    }

    @Override
    public void cancel() {
        super.cancel();
        stop();
    }
}
