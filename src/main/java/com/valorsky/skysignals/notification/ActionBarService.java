package com.valorsky.skysignals.notification;

import com.valorsky.skysignals.config.Config;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ActionBarService {

    private final JavaPlugin plugin;
    private final Config config;
    private final Map<UUID, String> lastMessages = new ConcurrentHashMap<>();

    public ActionBarService(JavaPlugin plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void send(Component message, List<Player> audience) {
        if (!config.actionBarEnabled()) return;

        for (Player player : audience) {
            String key = message.toString();
            String last = lastMessages.get(player.getUniqueId());
            if (!key.equals(last)) {
                player.sendActionBar(message);
                lastMessages.put(player.getUniqueId(), key);
            }
        }
    }

    public void send(Component message, Player player) {
        if (!config.actionBarEnabled()) return;
        String key = message.toString();
        String last = lastMessages.get(player.getUniqueId());
        if (!key.equals(last)) {
            player.sendActionBar(message);
            lastMessages.put(player.getUniqueId(), key);
        }
    }

    public void clear(Player player) {
        player.sendActionBar(Component.empty());
        lastMessages.remove(player.getUniqueId());
    }

    public void clearAll(List<Player> audience) {
        for (Player player : audience) {
            clear(player);
        }
    }
}