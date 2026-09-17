package com.valorsky.skysignals.island;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;

public final class DefaultIslandProvider implements IslandProvider {

    private final JavaPlugin plugin;

    public DefaultIslandProvider(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Optional<Island> getIsland(UUID playerId) {
        return Optional.empty();
    }

    @Override
    public Optional<Island> getIslandAt(Location location) {
        return Optional.empty();
    }

    @Override
    public Optional<Island> getIslandByName(String name) {
        return Optional.empty();
    }
}