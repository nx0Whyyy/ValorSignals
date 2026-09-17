package com.valorsky.skysignals.config;

import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigManager {

    private final JavaPlugin plugin;
    private Config config;
    private MessageConfig messageConfig;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        config = new Config(plugin);
        config.load();
        messageConfig = new MessageConfig(plugin);
        messageConfig.load();
    }

    public void reload() {
        config.reload();
        messageConfig.reload();
    }

    public Config config() {
        return config;
    }

    public MessageConfig messages() {
        return messageConfig;
    }
}
