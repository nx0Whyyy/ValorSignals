package com.valorsky.skysignals;

import com.valorsky.skysignals.api.SkySignalsAPI;
import com.valorsky.skysignals.cache.CacheService;
import com.valorsky.skysignals.cache.LocalEventCache;
import com.valorsky.skysignals.command.SkySignalsCommand;
import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.config.ConfigManager;
import com.valorsky.skysignals.database.DatabaseManager;
import com.valorsky.skysignals.database.EventRepository;
import com.valorsky.skysignals.event.SkyEventFactory;
import com.valorsky.skysignals.event.SkyEventManager;
import com.valorsky.skysignals.event.impl.*;
import com.valorsky.skysignals.listener.EventListener;
import com.valorsky.skysignals.model.SkyEventType;
import com.valorsky.skysignals.notification.NotificationService;
import com.valorsky.skysignals.rabbitmq.EventConsumer;
import com.valorsky.skysignals.rabbitmq.EventPublisher;
import com.valorsky.skysignals.rabbitmq.RabbitManager;
import com.valorsky.skysignals.redis.RedisService;
import com.valorsky.skysignals.reward.RewardService;
import com.valorsky.skysignals.scheduler.SignalScheduler;
import org.bukkit.plugin.java.JavaPlugin;

public final class SkySignalsPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private SkyEventFactory factory;
    private SkyEventManager eventManager;
    private CacheService cacheService;
    private LocalEventCache localCache;
    private DatabaseManager databaseManager;
    private RedisService redisService;
    private RabbitManager rabbitManager;
    private EventPublisher eventPublisher;
    private EventConsumer eventConsumer;
    private NotificationService notificationService;
    private RewardService rewardService;
    private SignalScheduler scheduler;
    private SkySignalsAPI api;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        configManager.initialize();
        Config config = configManager.config();

        cacheService = new CacheService(config);
        localCache = new LocalEventCache(config.cacheMaximumSize(), config.cacheExpireAfterMinutes());
        notificationService = new NotificationService(this, config, configManager.messages());
        rewardService = new RewardService(this, config);

        factory = new SkyEventFactory();
        registerEvents();

        databaseManager = new DatabaseManager(config, getLogger());
        databaseManager.connect();

        EventRepository eventRepository = new EventRepository(
                databaseManager.getDataSource(),
                getLogger()
        );

        redisService = new RedisService(config, getLogger());
        redisService.connect();

        rabbitManager = new RabbitManager(config, getLogger());
        rabbitManager.connect();

        eventPublisher = new EventPublisher(rabbitManager, getLogger());

        eventManager = new SkyEventManager(
                this, factory, cacheService, localCache,
                redisService, eventPublisher, eventRepository,
                notificationService, config
        );

        eventConsumer = new EventConsumer(rabbitManager, config, getLogger(), eventManager);

        eventManager.initialize();

        if (redisService.isConnected()) {
            redisService.addListener(new RedisService.RedisEventListener() {
                @Override
                public void onEventPublished(com.valorsky.skysignals.model.EventState state) {
                    getServer().getScheduler().runTask(SkySignalsPlugin.this, () -> {
                        switch (state.status()) {
                            case ACTIVE -> eventManager.handleEventStarted(state);
                            case FINISHED, CANCELLED -> eventManager.handleEventFinished(state);
                        }
                    });
                }
            });
        }

        if (rabbitManager.isConnected()) {
            eventConsumer.startConsuming();
        }

        api = new SkySignalsAPI(
                eventManager, cacheService, redisService,
                rabbitManager, databaseManager, factory
        );

        SkySignalsCommand cmd = new SkySignalsCommand(
                this, api, config, configManager.messages(), cacheService,
                redisService, rabbitManager, notificationService
        );
        getCommand("skysignals").setExecutor(cmd);
        getCommand("skysignals").setTabCompleter(cmd);

        getServer().getPluginManager().registerEvents(new EventListener(this, eventManager), this);

        scheduler = new SignalScheduler(this, config, eventManager);
        scheduler.start();

        getLogger().info("Plugin enabled.");
        getLogger().info("Registered " + factory.getRegisteredTypes().size() + " event types.");
    }

    private void registerEvents() {
        Config config = configManager.config();

        factory.register(SkyEventType.METEOR,
                state -> new MeteorEvent(state, this, notificationService, rewardService));
        factory.register(SkyEventType.STORM,
                state -> new StormEvent(state, this, notificationService, rewardService));
        factory.register(SkyEventType.SKY_CHEST,
                state -> new SkyChestEvent(state, this, notificationService, rewardService));
        factory.register(SkyEventType.MOB_INVASION,
                state -> new MobInvasionEvent(state, this, notificationService));
        factory.register(SkyEventType.MINERAL_RAIN,
                state -> new MineralRainEvent(state, this, notificationService));
        factory.register(SkyEventType.GROWTH_BOOST,
                state -> new GrowthBoostEvent(state, this, notificationService));
    }

    @Override
    public void onDisable() {
        if (scheduler != null) scheduler.stop();
        if (eventManager != null) eventManager.shutdown();
        if (notificationService != null) notificationService.cleanup();
        if (redisService != null) redisService.disconnect();
        if (rabbitManager != null) rabbitManager.disconnect();
        if (databaseManager != null) databaseManager.disconnect();

        getLogger().info("Plugin disabled.");
    }

    public void onReload() {
        if (configManager != null) configManager.reload();
        if (scheduler != null) scheduler.reload();
        if (eventManager != null) eventManager.reload();
        getLogger().info("Plugin reloaded.");
    }

    public SkySignalsAPI getApi() {
        return api;
    }
}
