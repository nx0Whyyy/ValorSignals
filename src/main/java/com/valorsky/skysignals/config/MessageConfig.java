package com.valorsky.skysignals.config;

import java.io.File;
import org.bukkit.plugin.java.JavaPlugin;

public final class MessageConfig {

    private final JavaPlugin plugin;
    private org.bukkit.configuration.file.FileConfiguration messages;

    public MessageConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveResource("messages.yml", false);
        messages = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), "messages.yml")
        );
    }

    public String get(String path) {
        String value = messages.getString(path, "");
        if (!isLoaded() && value.isEmpty()) {
            plugin.getLogger().warning("Missing message: " + path);
        }
        return value;
    }

    public String get(String path, String def) {
        return messages.getString(path, def);
    }

    public String getWithPrefix(String path) {
        String prefix = messages.getString("prefix", "");
        String message = messages.getString(path, "");
        return prefix + " " + message;
    }

    public boolean isLoaded() {
        return messages != null;
    }

    public void reload() {
        load();
    }

    public String prefix() {
        return messages.getString("prefix", "");
    }
}
