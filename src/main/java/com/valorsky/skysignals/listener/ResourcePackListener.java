package com.valorsky.skysignals.listener;

import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.util.FoliaScheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HexFormat;
import java.util.logging.Level;

public final class ResourcePackListener implements Listener {
    private final JavaPlugin plugin;
    private final Config config;

    public ResourcePackListener(JavaPlugin plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!config.resourcePackEnabled()) return;
        Player player = event.getPlayer();
        FoliaScheduler.runEntityDelayed(plugin, player, () -> sendPack(player), 20L);
    }

    private void sendPack(Player player) {
        String url = config.resourcePackUrl().trim();
        String sha1 = config.resourcePackSha1().trim();
        if (url.isEmpty()) {
            plugin.getLogger().warning("Resource pack is enabled but resource-pack.url is empty.");
            return;
        }
        try {
            byte[] hash = HexFormat.of().parseHex(sha1);
            if (hash.length != 20) throw new IllegalArgumentException("SHA-1 must contain 40 hexadecimal characters");
            player.setResourcePack(url, hash,
                    Component.text("Le pack SkySignals est nécessaire pour afficher la météorite 3D."),
                    config.resourcePackRequired());
        } catch (IllegalArgumentException error) {
            plugin.getLogger().log(Level.SEVERE, "Invalid SkySignals resource-pack configuration", error);
        }
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        switch (event.getStatus()) {
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> {
                plugin.getLogger().warning("Resource pack failed for " + event.getPlayer().getName()
                        + ": " + event.getStatus());
                event.getPlayer().sendMessage(Component.text(
                        "Le pack SkySignals n'a pas pu être chargé : " + event.getStatus()));
            }
            default -> { }
        }
    }
}
