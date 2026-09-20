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

    public java.util.concurrent.CompletableFuture<Optional<Location>> findNearPlayersAsync(double min, double max) {
        Player player = plugin.getServer().getOnlinePlayers().stream().findFirst().orElse(null);
        if (player == null) return java.util.concurrent.CompletableFuture.completedFuture(Optional.empty());
        var result = new java.util.concurrent.CompletableFuture<Optional<Location>>();
        var task = player.getScheduler().run(plugin, t -> {
            Location center = player.getLocation();
            findCandidate(center, min, max, 50, result);
        }, () -> result.complete(Optional.empty()));
        if (task == null) result.complete(Optional.empty());
        return result;
    }

    private void findCandidate(Location center, double min, double max, int remaining,
                               java.util.concurrent.CompletableFuture<Optional<Location>> result) {
        if (remaining <= 0 || !plugin.isEnabled()) { result.complete(Optional.empty()); return; }
        double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        double distance = min + ThreadLocalRandom.current().nextDouble() * (max - min);
        Location candidate = center.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
        try {
            com.valorsky.skysignals.util.FoliaScheduler.runRegion(plugin, candidate, () -> {
                try {
                    if (isChunkLoaded(candidate)) {
                        candidate.setY(candidate.getWorld().getHighestBlockYAt(candidate) + 1);
                        if (isValidLocation(candidate)) { result.complete(Optional.of(candidate)); return; }
                    }
                    findCandidate(center, min, max, remaining - 1, result);
                } catch (Exception e) { result.completeExceptionally(e); }
            });
        } catch (Exception e) { result.completeExceptionally(e); }
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

            Location candidate = new Location(world, x, center.getY(), z);
            if (!isChunkLoaded(candidate) || !org.bukkit.Bukkit.isOwnedByCurrentRegion(candidate)) continue;
            candidate.setY(world.getHighestBlockYAt(x, z) + 1);

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
            if (!world.isChunkLoaded(x >> 4, z >> 4) || !org.bukkit.Bukkit.isOwnedByCurrentRegion(new Location(world, x, 0, z))) continue;
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

        if (!isChunkLoaded(loc) || !org.bukkit.Bukkit.isOwnedByCurrentRegion(loc)) return false;
        if (!loc.getWorld().getWorldBorder().isInside(loc)) return false;
        if (!loc.getBlock().isEmpty() || !loc.clone().add(0, 1, 0).getBlock().isEmpty()) return false;
        if (!loc.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) return false;
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