package com.valorsky.skysignals.event;

import com.valorsky.skysignals.animation.AnimationService;
import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.island.IslandProvider;
import com.valorsky.skysignals.location.SafeLocationService;
import com.valorsky.skysignals.notification.ActionBarService;
import com.valorsky.skysignals.notification.BossBarService;
import com.valorsky.skysignals.notification.TitleService;
import com.valorsky.skysignals.particle.ParticleService;
import com.valorsky.skysignals.protection.ProtectionProvider;
import com.valorsky.skysignals.reward.RewardService;
import com.valorsky.skysignals.scheduler.SignalScheduler;
import com.valorsky.skysignals.sound.SoundService;
import org.bukkit.plugin.java.JavaPlugin;

public final class EventContext {
    private final JavaPlugin plugin;
    private final Config config;
    private final AnimationService animationService;
    private final ParticleService particleService;
    private final SoundService soundService;
    private final TitleService titleService;
    private final ActionBarService actionBarService;
    private final BossBarService bossBarService;
    private final SafeLocationService locationService;
    private final ProtectionProvider protectionProvider;
    private final IslandProvider islandProvider;
    private final RewardService rewardService;
    private SignalScheduler scheduler;

    public EventContext(
            JavaPlugin plugin,
            Config config,
            AnimationService animationService,
            ParticleService particleService,
            SoundService soundService,
            TitleService titleService,
            ActionBarService actionBarService,
            BossBarService bossBarService,
            SafeLocationService locationService,
            ProtectionProvider protectionProvider,
            IslandProvider islandProvider,
            RewardService rewardService,
            SignalScheduler scheduler
    ) {
        this.plugin = plugin;
        this.config = config;
        this.animationService = animationService;
        this.particleService = particleService;
        this.soundService = soundService;
        this.titleService = titleService;
        this.actionBarService = actionBarService;
        this.bossBarService = bossBarService;
        this.locationService = locationService;
        this.protectionProvider = protectionProvider;
        this.islandProvider = islandProvider;
        this.rewardService = rewardService;
        this.scheduler = scheduler;
    }

    public JavaPlugin getPlugin() { return plugin; }
    public Config getConfig() { return config; }
    public AnimationService getAnimationService() { return animationService; }
    public ParticleService getParticleService() { return particleService; }
    public SoundService getSoundService() { return soundService; }
    public TitleService getTitleService() { return titleService; }
    public ActionBarService getActionBarService() { return actionBarService; }
    public BossBarService getBossBarService() { return bossBarService; }
    public SafeLocationService getLocationService() { return locationService; }
    public ProtectionProvider getProtectionProvider() { return protectionProvider; }
    public IslandProvider getIslandProvider() { return islandProvider; }
    public RewardService getRewardService() { return rewardService; }
    public SignalScheduler getScheduler() { return scheduler; }

    public void setScheduler(SignalScheduler scheduler) {
        this.scheduler = scheduler;
    }
}