package com.valorsky.skysignals.config;

import com.valorsky.skysignals.model.SkyEventType;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class Config {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public Config(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        config = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    public String serverId() {
        return config.getString("server.id", "SkyBlock-01");
    }

    public boolean debug() {
        return config.getBoolean("debug", false);
    }

    public boolean schedulerEnabled() {
        return config.getBoolean("scheduler.enabled", true);
    }

    public int schedulerMinInterval() {
        return config.getInt("scheduler.min-interval", 300);
    }

    public int schedulerMaxInterval() {
        return config.getInt("scheduler.max-interval", 900);
    }

    public Set<SkyEventType> getAllowedSchedulerEvents() {
        List<String> list = config.getStringList("scheduler.allowed-events");
        if (list.isEmpty()) {
            return Set.of(SkyEventType.values());
        }
        Set<SkyEventType> set = new HashSet<>();
        for (String s : list) {
            SkyEventType type = SkyEventType.fromId(s);
            if (type != null) set.add(type);
        }
        return set;
    }

    public Map<SkyEventType, Integer> getEventWeights() {
        Map<SkyEventType, Integer> weights = new EnumMap<>(SkyEventType.class);
        for (SkyEventType type : SkyEventType.values()) {
            String path = "event-weights." + type.configKey();
            weights.put(type, config.getInt(path, 10));
        }
        return weights;
    }

    public boolean eventsAllowConcurrent() {
        return config.getBoolean("events.allow-concurrent", false);
    }

    public int eventsMaxActive() {
        return config.getInt("events.max-active", 1);
    }

    public Map<SkyEventType, Set<SkyEventType>> getEventConflicts() {
        Map<SkyEventType, Set<SkyEventType>> conflicts = new EnumMap<>(SkyEventType.class);
        for (SkyEventType type : SkyEventType.values()) {
            String path = "conflicts." + type.configKey();
            List<String> list = config.getStringList(path);
            Set<SkyEventType> set = new HashSet<>();
            for (String s : list) {
                SkyEventType t = SkyEventType.fromId(s);
                if (t != null) set.add(t);
            }
            conflicts.put(type, set);
        }
        return conflicts;
    }

    public int globalCooldown() {
        return config.getInt("cooldowns.global-event", 300);
    }

    public int playerParticipationCooldown() {
        return config.getInt("cooldowns.player-participation", 60);
    }

    public boolean visualsEnabled() {
        return config.getBoolean("visuals.enabled", true);
    }

    public int viewDistance() {
        return config.getInt("visuals.view-distance", 48);
    }

    public boolean particlesEnabled() {
        return config.getBoolean("visuals.particles.enabled", true);
    }

    public int maxParticlesPerSecond() {
        return config.getInt("visuals.particles.max-per-second", 250);
    }

    public boolean soundsEnabled() {
        return config.getBoolean("visuals.sounds.enabled", true);
    }

    public boolean titlesEnabled() {
        return config.getBoolean("visuals.titles.enabled", true);
    }

    public int titleFadeIn() {
        return config.getInt("visuals.titles.fade-in", 10);
    }

    public int titleStay() {
        return config.getInt("visuals.titles.stay", 40);
    }

    public int titleFadeOut() {
        return config.getInt("visuals.titles.fade-out", 20);
    }

    public boolean actionBarEnabled() {
        return config.getBoolean("visuals.actionbar.enabled", true);
    }

    public boolean bossBarEnabled() {
        return config.getBoolean("visuals.bossbar.enabled", true);
    }

    public int performanceMaxEventEntities() {
        return config.getInt("performance.max-event-entities", 30);
    }

    public int performanceMaxMineralItems() {
        return config.getInt("performance.max-mineral-items", 25);
    }

    public boolean isEventEnabled(SkyEventType type) {
        String path = "events-config." + type.configKey() + ".enabled";
        return config.getBoolean(path, true);
    }

    public int getEventDuration(SkyEventType type) {
        String path = "events-config." + type.configKey() + ".duration";
        return config.getInt(path, 300);
    }

    public int getEventWarningDuration(SkyEventType type) {
        String path = "events-config." + type.configKey() + ".warning-duration";
        return config.getInt(path, 15);
    }

    public boolean getMeteorTerrainDestruction() {
        return config.getBoolean("events-config.meteor.terrain-destruction", false);
    }

    public List<String> getMeteorAllowedBlocks() {
        return config.getStringList("events-config.meteor.allowed-blocks");
    }

    public int getMobInvasionMaxMobs() {
        return config.getInt("events-config.mob-invasion.max-mobs", 20);
    }

    public List<MobWaveConfig> getMobInvasionWaves() {
        List<?> raw = config.getList("events-config.mob-invasion.waves");
        List<MobWaveConfig> waves = new ArrayList<>();
        if (raw != null) {
            for (Object obj : raw) {
                if (obj instanceof Map<?, ?> map) {
                    int delay = map.get("delay") instanceof Number n ? n.intValue() : 0;
                    Map<String, Integer> mobs = new HashMap<>();
                    Object mobsObj = map.get("mobs");
                    if (mobsObj instanceof Map<?, ?> mobsMap) {
                        for (Map.Entry<?, ?> entry : mobsMap.entrySet()) {
                            mobs.put(entry.getKey().toString(), ((Number) entry.getValue()).intValue());
                        }
                    }
                    waves.add(new MobWaveConfig(delay, mobs));
                }
            }
        }
        if (waves.isEmpty()) {
            waves.add(new MobWaveConfig(0, Map.of("ZOMBIE", 5, "SKELETON", 3)));
            waves.add(new MobWaveConfig(30, Map.of("ZOMBIE", 8, "SPIDER", 3)));
            waves.add(new MobWaveConfig(30, Map.of("CREEPER", 2, "SKELETON", 5)));
        }
        return waves;
    }

    public int getMineralRainMaxActiveItems() {
        return config.getInt("events-config.mineral-rain.max-active-items", 25);
    }

    public int getMineralRainSpawnInterval() {
        return config.getInt("events-config.mineral-rain.spawn-interval", 10);
    }

    public Map<Material, Integer> getMineralRainDrops() {
        Map<Material, Integer> drops = new EnumMap<>(Material.class);
        List<?> raw = config.getList("events-config.mineral-rain.drops");
        if (raw != null) {
            for (Object obj : raw) {
                if (obj instanceof Map<?, ?> map) {
                    String materialStr = (String) map.get("material");
                    Material material = Material.matchMaterial(materialStr);
                    if (material != null) {
                        int weight = map.get("weight") instanceof Number n ? n.intValue() : 10;
                        drops.put(material, weight);
                    }
                }
            }
        }
        if (drops.isEmpty()) {
            drops.put(Material.DIAMOND, 5);
            drops.put(Material.GOLD_INGOT, 15);
            drops.put(Material.IRON_INGOT, 25);
            drops.put(Material.COAL, 40);
        }
        return drops;
    }

    public double getGrowthBoostMultiplier() {
        return config.getDouble("events-config.growth-boost.multiplier", 2.0);
    }

    public int getGrowthBoostVisualCooldown() {
        return config.getInt("events-config.growth-boost.visual.particle-cooldown", 20);
    }

    public boolean redisEnabled() {
        return config.getBoolean("redis.enabled", true);
    }

    public String redisUri() {
        return config.getString("redis.uri", "redis://localhost:6379");
    }

    public String redisPassword() {
        return envOrConfig("redis.password", "REDIS_PASSWORD", "");
    }

    public String redisChannel() {
        return config.getString("redis.channel", "valorsky.skysignals");
    }

    public boolean rabbitEnabled() {
        return config.getBoolean("rabbitmq.enabled", true);
    }

    public String rabbitHost() {
        return config.getString("rabbitmq.host", "localhost");
    }

    public int rabbitPort() {
        return config.getInt("rabbitmq.port", 5672);
    }

    public String rabbitUsername() {
        return envOrConfig("rabbitmq.username", "RABBITMQ_USERNAME", "guest");
    }

    public String rabbitPassword() {
        return envOrConfig("rabbitmq.password", "RABBITMQ_PASSWORD", "guest");
    }

    public String rabbitExchange() {
        return config.getString("rabbitmq.exchange", "valorsky.skysignals");
    }

    public String dbHost() {
        return envOrConfig("database.host", "DB_HOST", "localhost");
    }

    public int dbPort() {
        return config.getInt("database.port", 3306);
    }

    public String dbName() {
        return config.getString("database.database", "valorsky");
    }

    public String dbUsername() {
        return envOrConfig("database.username", "DB_USERNAME", "root");
    }

    public String dbPassword() {
        return envOrConfig("database.password", "DB_PASSWORD", "");
    }

    public int dbPoolSize() {
        return config.getInt("database.pool-size", 10);
    }

    public int cacheMaximumSize() {
        return config.getInt("cache.maximum-size", 100);
    }

    public int cacheExpireAfterMinutes() {
        return config.getInt("cache.expire-after-minutes", 10);
    }

    public int minHeight() {
        return config.getInt("location.min-height", 60);
    }

    public int maxHeight() {
        return config.getInt("location.max-height", 250);
    }

    public boolean testingRewards() {
        return config.getBoolean("testing.rewards", false);
    }

    public boolean testingAnnouncements() {
        return config.getBoolean("testing.announcements", true);
    }

    public boolean testingVisuals() {
        return config.getBoolean("testing.visuals", true);
    }

    public int getRewardMoneyMin(SkyEventType type) {
        return config.getInt("rewards." + type.configKey() + ".money.min", 0);
    }

    public int getRewardMoneyMax(SkyEventType type) {
        return config.getInt("rewards." + type.configKey() + ".money.max", 0);
    }

    public List<String> getRewardCommands(SkyEventType type) {
        return config.getStringList("rewards." + type.configKey() + ".commands");
    }

    public boolean notificationsChat() {
        return config.getBoolean("notifications.chat", true);
    }

    private String envOrConfig(String configPath, String envVar, String defaultValue) {
        String envValue = System.getenv(envVar);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        String configValue = config.getString(configPath, defaultValue);
        return configValue != null ? configValue : defaultValue;
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public record MobWaveConfig(int delay, Map<String, Integer> mobs) {}
}