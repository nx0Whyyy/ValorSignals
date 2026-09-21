package com.valorsky.skysignals;

import com.valorsky.skysignals.api.SkySignalsAPI;
import com.valorsky.skysignals.cache.CacheService;
import com.valorsky.skysignals.cache.LocalEventCache;
import com.valorsky.skysignals.command.SkySignalsCommand;
import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.config.ConfigManager;
import com.valorsky.skysignals.config.MessageConfig;
import com.valorsky.skysignals.database.DatabaseManager;
import com.valorsky.skysignals.database.EventRepository;
import com.valorsky.skysignals.event.EventContext;
import com.valorsky.skysignals.event.SkyEventFactory;
import com.valorsky.skysignals.event.SkyEventManager;
import com.valorsky.skysignals.event.impl.*;
import com.valorsky.skysignals.island.DefaultIslandProvider;
import com.valorsky.skysignals.island.IslandProvider;
import com.valorsky.skysignals.listener.EventListener;
import com.valorsky.skysignals.listener.ResourcePackListener;
import com.valorsky.skysignals.model.SkyEventType;
import com.valorsky.skysignals.notification.ActionBarService;
import com.valorsky.skysignals.notification.BossBarService;
import com.valorsky.skysignals.notification.NotificationService;
import com.valorsky.skysignals.notification.TitleService;
import com.valorsky.skysignals.particle.ParticleService;
import com.valorsky.skysignals.protection.DefaultProtectionProvider;
import com.valorsky.skysignals.protection.ProtectionProvider;
import com.valorsky.skysignals.rabbitmq.EventConsumer;
import com.valorsky.skysignals.rabbitmq.EventPublisher;
import com.valorsky.skysignals.rabbitmq.RabbitManager;
import com.valorsky.skysignals.redis.DistributedLockService;
import com.valorsky.skysignals.redis.RedisService;
import com.valorsky.skysignals.reward.RewardService;
import com.valorsky.skysignals.scheduler.SignalScheduler;
import com.valorsky.skysignals.sound.SoundService;
import com.valorsky.skysignals.location.SafeLocationService;
import com.valorsky.skysignals.util.FoliaScheduler;
import org.bukkit.plugin.java.JavaPlugin;

public final class SkySignalsPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private MessageConfig messageConfig;
    private SkyEventFactory factory;
    private SkyEventManager eventManager;
    private CacheService cacheService;
    private LocalEventCache localCache;
    private DatabaseManager databaseManager;
    private RedisService redisService;
    private DistributedLockService lockService;
    private RabbitManager rabbitManager;
    private EventPublisher eventPublisher;
    private EventConsumer eventConsumer;
    private NotificationService notificationService;
    private RewardService rewardService;
    private SignalScheduler scheduler;
    private SkySignalsAPI api;

    // Services
    private ParticleService particleService;
    private SoundService soundService;
    private TitleService titleService;
    private ActionBarService actionBarService;
    private BossBarService bossBarService;
    private SafeLocationService locationService;
    private ProtectionProvider protectionProvider;
    private IslandProvider islandProvider;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        FoliaScheduler.init(getLogger());

        configManager = new ConfigManager(this);
        configManager.initialize();
        messageConfig = configManager.messages();
        Config config = configManager.config();

        // Core services
        cacheService = new CacheService(config);
        localCache = new LocalEventCache(config.cacheMaximumSize(), config.cacheExpireAfterMinutes());

        // Notification services
        titleService = new TitleService(this, config);
        actionBarService = new ActionBarService(this, config);
        bossBarService = new BossBarService(this, config);
        soundService = new SoundService(this, config);
        particleService = new ParticleService(this, config);

        // Location & protection
        protectionProvider = new DefaultProtectionProvider();
        islandProvider = new DefaultIslandProvider(this);
        locationService = new SafeLocationService(this, config, protectionProvider, islandProvider);

        // Database
        databaseManager = new DatabaseManager(this, config, getLogger());
        databaseManager.connect();

        EventRepository eventRepository = new EventRepository(
                databaseManager::getDataSource,
                getLogger()
        );

        // Redis
        redisService = new RedisService(this, config, getLogger());
        redisService.connect();

        lockService = new DistributedLockService(this, config, redisService.getClient(), getLogger());

        // RabbitMQ
        rabbitManager = new RabbitManager(this, config, getLogger());
        rabbitManager.connect();

        eventPublisher = new EventPublisher(this, rabbitManager, getLogger());

        // Reward service
        rewardService = new RewardService(this, config, databaseManager);

        // Notification service
        notificationService = new NotificationService(
                this, config, messageConfig,
                titleService, actionBarService, bossBarService, soundService
        );

        // Event factory & manager
        factory = new SkyEventFactory();
        registerEvents();

        eventManager = new SkyEventManager(
                this, factory, cacheService, localCache,
                redisService, eventPublisher, eventRepository,
                notificationService, config
        );

        // Event context for DI (scheduler set later)
        EventContext eventContext = new EventContext(
                this, config,
                new com.valorsky.skysignals.animation.AnimationService(this),
                particleService, soundService,
                titleService, actionBarService, bossBarService,
                locationService, protectionProvider, islandProvider,
                rewardService, null
        );

        scheduler = new SignalScheduler(this, config, eventManager, lockService, eventContext);
        eventContext.setScheduler(scheduler);
        eventManager.setEventContext(eventContext);

        eventConsumer = new EventConsumer(rabbitManager, config, getLogger(), eventManager, this);

        rabbitManager.onConnected(eventConsumer::startConsuming);
        eventManager.initialize();

        {
            redisService.addListener(state -> {
                FoliaScheduler.runGlobal(this, () -> {
                    switch (state.status()) {
                        case ANNOUNCING, WARNING, ACTIVE, COMPLETING -> eventManager.handleEventStarted(state);
                        case FINISHED, CANCELLED -> eventManager.handleEventFinished(state);
                    }
                });
            });
        }
        getLogger().info(redisService.isConnected() ? "Redis connected." : "Redis unavailable: using local scheduling.");

        if (rabbitManager.isConnected()) {
            eventConsumer.startConsuming();
            getLogger().info("RabbitMQ: connected - messaging enabled.");
        } else {
            getLogger().info("RabbitMQ: unavailable - messaging disabled.");
        }

        if (databaseManager.isConnected()) {
            getLogger().info("Database: connected - persistence enabled.");
        } else {
            getLogger().info("Database: unavailable - persistence disabled.");
        }

        api = new SkySignalsAPI(
                eventManager, cacheService, redisService,
                rabbitManager, databaseManager, factory
        );

        SkySignalsCommand cmd = new SkySignalsCommand(
                this, api, config, messageConfig, cacheService,
                redisService, rabbitManager, notificationService,
                eventContext
        );
        getCommand("skysignals").setExecutor(cmd);
        getCommand("skysignals").setTabCompleter(cmd);

        getServer().getPluginManager().registerEvents(new EventListener(this, eventManager), this);
        getServer().getPluginManager().registerEvents(new ResourcePackListener(this, config), this);

        scheduler.start();

        getLogger().info("SkySignals enabled successfully. Registered " + factory.getRegisteredTypes().size() + " event types.");
    }

    private void registerEvents() {
        Config config = configManager.config();

        factory.register(SkyEventType.METEOR,
                (state, ctx) -> new MeteorEvent(state, this, notificationService, rewardService,
                        particleService, soundService, locationService, config));
        factory.register(SkyEventType.STORM,
                (state, ctx) -> new StormEvent(state, this, notificationService, rewardService,
                        particleService, soundService));
        factory.register(SkyEventType.SKY_CHEST,
                (state, ctx) -> new SkyChestEvent(state, this, notificationService, rewardService,
                        particleService, soundService, locationService, config));
        factory.register(SkyEventType.MOB_INVASION,
                (state, ctx) -> new MobInvasionEvent(state, this, notificationService, rewardService,
                        particleService, soundService, locationService, config));
        factory.register(SkyEventType.MINERAL_RAIN,
                (state, ctx) -> new MineralRainEvent(state, this, notificationService, rewardService,
                        particleService, soundService, locationService, config));
        factory.register(SkyEventType.GROWTH_BOOST,
                (state, ctx) -> new GrowthBoostEvent(state, this, notificationService,
                        particleService, soundService, config));
    }

    @Override
    public void onDisable() {
        cleanup("scheduler", () -> { if (scheduler != null) scheduler.stop(); });
        cleanup("events", () -> { if (eventManager != null) eventManager.shutdown(); });
        cleanup("notifications", () -> { if (notificationService != null) notificationService.cleanup(); });
        cleanup("locks", () -> { if (lockService != null) lockService.shutdown(); });
        cleanup("Redis", () -> { if (redisService != null) redisService.disconnect(); });
        cleanup("RabbitMQ", () -> { if (rabbitManager != null) rabbitManager.disconnect(); });
        cleanup("database", () -> { if (databaseManager != null) databaseManager.disconnect(); });
        org.bukkit.Bukkit.getGlobalRegionScheduler().cancelTasks(this);
        org.bukkit.Bukkit.getAsyncScheduler().cancelTasks(this);
        getLogger().info("Plugin disabled.");
    }

    private void cleanup(String service, Runnable action) {
        try { action.run(); }
        catch (Exception e) { getLogger().log(java.util.logging.Level.WARNING, "Failed to stop " + service, e); }
    }

    public void onReload() {
        if (configManager != null) configManager.reload();
        if (eventManager != null) eventManager.reload();
        if (scheduler != null) scheduler.reload();
        getLogger().info("Plugin reloaded.");
    }

    public SkySignalsAPI getApi() {
        return api;
    }

    // Service getters for events
    public ParticleService getParticleService() { return particleService; }
    public SoundService getSoundService() { return soundService; }
    public SafeLocationService getLocationService() { return locationService; }
    public NotificationService getNotificationService() { return notificationService; }
    public RewardService getRewardService() { return rewardService; }
    public Config getPluginConfig() { return configManager.config(); }
    public MessageConfig getMessageConfig() { return messageConfig; }
}
