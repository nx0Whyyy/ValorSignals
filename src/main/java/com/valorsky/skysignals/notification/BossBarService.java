package com.valorsky.skysignals.notification;

import com.valorsky.skysignals.config.Config;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BossBarService {

    private final JavaPlugin plugin;
    private final Config config;
    private final Map<String, BossBar> activeBars = new ConcurrentHashMap<>();

    public BossBarService(JavaPlugin plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void create(String id, Component message, float progress, BossBar.Color color, BossBar.Overlay overlay, List<Player> audience) {
        if (!config.bossBarEnabled()) return;

        BossBar bossBar = BossBar.bossBar(message, progress, color, overlay);
        for (Player player : audience) {
            player.showBossBar(bossBar);
        }
        activeBars.put(id, bossBar);
    }

    public void create(String id, Component message, float progress, List<Player> audience) {
        create(id, message, progress, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS, audience);
    }

    public void update(String id, Component message, float progress) {
        BossBar bossBar = activeBars.get(id);
        if (bossBar != null) {
            bossBar.name(message);
            bossBar.progress(progress);
        }
    }

    public void updateProgress(String id, float progress) {
        BossBar bossBar = activeBars.get(id);
        if (bossBar != null) {
            bossBar.progress(progress);
        }
    }

    public void updateMessage(String id, Component message) {
        BossBar bossBar = activeBars.get(id);
        if (bossBar != null) {
            bossBar.name(message);
        }
    }

    public void remove(String id, List<Player> audience) {
        BossBar bossBar = activeBars.remove(id);
        if (bossBar != null) {
            for (Player player : audience) {
                player.hideBossBar(bossBar);
            }
        }
    }

    public void remove(String id) {
        BossBar bossBar = activeBars.remove(id);
        if (bossBar != null) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                player.hideBossBar(bossBar);
            }
        }
    }

    public void removeAll() {
        for (BossBar bossBar : activeBars.values()) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                player.hideBossBar(bossBar);
            }
        }
        activeBars.clear();
    }
}