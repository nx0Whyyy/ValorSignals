package com.valorsky.skysignals.config;

import com.valorsky.skysignals.model.SkyEventType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        return list.stream()
                .map(SkyEventType::fromId)
                .filter(type -> type != null)
                .collect(Collectors.toSet());
    }

    public boolean eventsAllowConcurrent() {
        return config.getBoolean("events.allow-concurrent", false);
    }

    public int eventsMaxActive() {
        return config.getInt("events.max-active", 1);
    }

    public boolean isEventEnabled(SkyEventType type) {
        String path = "events." + type.name().toLowerCase().replace("_", "-") + ".enabled";
        return config.getBoolean(path, true);
    }

    public int getEventDuration(SkyEventType type) {
        String path = "events." + type.name().toLowerCase().replace("_", "-") + ".duration";
        return config.getInt(path, 300);
    }

    public boolean redisEnabled() {
        return config.getBoolean("redis.enabled", true);
    }

    public String redisUri() {
        return config.getString("redis.uri", "redis://localhost:6379");
    }

    public String redisChannel() {
        return config.getString("redis.channel", "valorsky.skysignals");
    }

    public String redisPassword() {
        return config.getString("redis.password", "");
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

    public boolean notificationsChat() {
        return config.getBoolean("notifications.chat", true);
    }

    public boolean notificationsActionbar() {
        return config.getBoolean("notifications.actionbar", true);
    }

    public boolean notificationsBossbar() {
        return config.getBoolean("notifications.bossbar", true);
    }

    public boolean notificationsSound() {
        return config.getBoolean("notifications.sound", true);
    }

    public boolean notificationsParticles() {
        return config.getBoolean("notifications.particles", true);
    }

    public List<String> getRewardCommands(SkyEventType type) {
        return config.getStringList(getRewardPath(type) + ".commands");
    }

    public int getRewardMoneyMin(SkyEventType type) {
        return config.getInt(getRewardPath(type) + ".money.min", 0);
    }

    public int getRewardMoneyMax(SkyEventType type) {
        return config.getInt(getRewardPath(type) + ".money.max", 0);
    }

    private String getRewardPath(SkyEventType type) {
        return "rewards." + type.name().toLowerCase().replace("_", "-");
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
}
