package com.valorsky.skysignals.island;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;

public interface IslandProvider {
    Optional<Island> getIsland(UUID playerId);
    Optional<Island> getIslandAt(Location location);
    Optional<Island> getIslandByName(String name);
}