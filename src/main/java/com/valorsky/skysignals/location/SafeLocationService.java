package com.valorsky.skysignals.location;

import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.island.IslandProvider;
import com.valorsky.skysignals.protection.ProtectionProvider;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public final class SafeLocationService {

    private final JavaPlugin plugin;
    private final Config config;
    private final ProtectionProvider protectionProvider;
    private final IslandProvider islandProvider;
    private final Logger logger;

    public SafeLocationService(JavaPlugin plugin, Config config, ProtectionProvider protectionProvider, IslandProvider islandProvider) {
        this.plugin = plugin;
        this.config = config;
        this.protectionProvider = protectionProvider;
        this.islandProvider = islandProvider;
        this.logger = plugin.getLogger();
    }

    public Optional<Location> findSafeLocation(Location center, double minDistance, double maxDistance) {
        if (center == null || center.getWorld() == null) {
            return findRandomSafeLocation();
        }

        World world = center.getWorld();
        int maxAttempts = 50;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            double distance = minDistance + ThreadLocalRandom.current().nextDouble() * (maxDistance - minDistance);
            int x = center.getBlockX() + (int) (distance * Math.cos(angle));
            int z = center.getBlockZ() + (int) (distance * Math.sin(angle));

            Location candidate = new Location(world, x, world.getHighestBlockYAt(x, z) + 1, z);

            if (isValidLocation(candidate)) {
                return Optional.of(candidate);
            }
        }

        logger.warning("Could not find safe location near " + center + " after " + maxAttempts + " attempts");
        return findRandomSafeLocation();
    }

    public Optional<Location> findRandomSafeLocation() {
        List<World> worlds = plugin.getServer().getWorlds().stream()
            .filter(w -> w.getEnvironment() == World.Environment.NORMAL)
            .toList();

        if (worlds.isEmpty()) {
            logger.warning("No normal worlds available for safe location");
            return Optional.empty();
        }

        World world = worlds.get(ThreadLocalRandom.current().nextInt(worlds.size()));
        int maxAttempts = 100;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = ThreadLocalRandom.current().nextInt(-10000, 10000);
            int z = ThreadLocalRandom.current().nextInt(-10000, 10000);
            int y = world.getHighestBlockYAt(x, z) + 1;

            Location candidate = new Location(world, x, y, z);

            if (isValidLocation(candidate)) {
                return Optional.of(candidate);
            }
        }

        logger.warning("Could not find any safe location after " + maxAttempts + " attempts");
        return Optional.empty();
    }

    public Optional<Location> findIslandLocation(UUID playerId) {
        return islandProvider.getIsland(playerId)
            .flatMap(island -> island.getCenter())
            .flatMap(center -> findSafeLocation(center, 0, 50));
    }

    public Optional<Location> findIslandLocation(Location near) {
        return islandProvider.getIslandAt(near)
            .flatMap(island -> island.getCenter())
            .flatMap(center -> findSafeLocation(center, 0, 50));
    }

    private boolean isValidLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        if (!protectionProvider.canModify(loc)) return false;

        if (loc.getBlockY() < config.minHeight() || loc.getBlockY() > config.maxHeight()) return false;

        if (!isChunkLoaded(loc)) return false;

        if (isInVoid(loc)) return false;

        if (isOnPlayer(loc)) return false;

        if (isInForbiddenZone(loc)) return false;

        return true;
    }

    private boolean isChunkLoaded(Location loc) {
        return loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    private boolean isInVoid(Location loc) {
        return loc.getBlock().getType().isAir() &&
               loc.clone().subtract(0, 1, 0).getBlock().getType().isAir() &&
               loc.clone().subtract(0, 2, 0).getBlock().getType().isAir();
    }

    private boolean isOnPlayer(Location loc) {
        for (Player player : loc.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(loc) < 4) {
                return true;
            }
        }
        return false;
    }

    private boolean isInForbiddenZone(Location loc) {
        Biome biome = loc.getBlock().getBiome();
        return biome == Biome.THE_VOID || biome == Biome.THE_END || biome == Biome.NETHER_WASTES;
    }
}