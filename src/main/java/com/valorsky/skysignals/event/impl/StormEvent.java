package com.valorsky.skysignals.event.impl;

import com.valorsky.skysignals.event.AbstractSkyEvent;
import com.valorsky.skysignals.model.EventState;
import com.valorsky.skysignals.model.SkyEventStatus;
import com.valorsky.skysignals.model.SkyEventType;
import com.valorsky.skysignals.notification.NotificationService;
import com.valorsky.skysignals.reward.RewardService;
import com.valorsky.skysignals.util.FoliaScheduler;
import com.valorsky.skysignals.util.FoliaScheduler.TaskHandle;
import org.bukkit.Bukkit;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Logger;

public final class StormEvent extends AbstractSkyEvent {

    private final NotificationService notificationService;
    private final RewardService rewardService;
    private final Logger logger;

    private boolean wasStorming = false;
    private boolean wasThundering = false;
    private transient TaskHandle stormTask;
    private World world;

    public StormEvent(EventState state, JavaPlugin plugin, NotificationService notificationService, RewardService rewardService) {
        super(state, plugin);
        this.notificationService = notificationService;
        this.rewardService = rewardService;
        this.logger = plugin.getLogger();
    }

    @Override
    public void start() {
        this.status = SkyEventStatus.ACTIVE;

        world = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().getEnvironment() == World.Environment.NORMAL)
                .map(Player::getWorld)
                .findFirst()
                .orElse(Bukkit.getWorld("world"));

        if (world == null) {
            logger.warning("No normal world found for storm event.");
            this.status = SkyEventStatus.CANCELLED;
            return;
        }

        wasStorming = world.hasStorm();
        wasThundering = world.isThundering();

        world.setStorm(true);
        world.setThundering(true);
        world.setWeatherDuration(999999);

        notificationService.notifyEventStart(this);

        stormTask = FoliaScheduler.runGlobalTimer(plugin, new Runnable() {
            private int tick = 0;

            @Override
            public void run() {
                if (world == null) {
                    return;
                }
                tick++;
                if (tick % 100 == 0) {
                    Player target = Bukkit.getOnlinePlayers().stream()
                            .filter(p -> p.getWorld().equals(world))
                            .findFirst()
                            .orElse(null);
                    if (target != null) {
                        org.bukkit.Location loc = target.getLocation();
                        FoliaScheduler.runRegion(plugin, loc, () -> {
                            world.strikeLightningEffect(loc);
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                if (p.getWorld().equals(world)) {
                                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
                                }
                            }
                        });
                    }
                }
            }
        }, 20L, 5L);

        logger.info("Storm event started on world " + world.getName());
    }

    @Override
    public void tick() {
    }

    @Override
    public void stop() {
        if (stormTask != null) {
            stormTask.cancel();
        }
        if (world != null) {
            FoliaScheduler.runGlobal(plugin, () -> {
                world.setStorm(wasStorming);
                world.setThundering(wasThundering);
                logger.info("Storm event stopped, weather restored.");
            });
        } else {
            logger.info("Storm event stopped, weather restored.");
        }
    }

    @Override
    public void cancel() {
        super.cancel();
        stop();
    }
}
