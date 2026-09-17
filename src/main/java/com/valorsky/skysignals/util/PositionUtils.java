package com.valorsky.skysignals.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

public final class PositionUtils {

    private static final Set<Material> SOLID_BLOCKS = new HashSet<>();
    private static final Set<Material> UNSAFE_BLOCKS = new HashSet<>();

    static {
        for (Material material : Material.values()) {
            if (material.isSolid() && material.isBlock()) {
                SOLID_BLOCKS.add(material);
            }
        }
        UNSAFE_BLOCKS.add(Material.LAVA);
        UNSAFE_BLOCKS.add(Material.WATER);
        UNSAFE_BLOCKS.add(Material.FIRE);
        UNSAFE_BLOCKS.add(Material.CACTUS);
        UNSAFE_BLOCKS.add(Material.SWEET_BERRY_BUSH);
    }

    private PositionUtils() {
    }

    public static Location findSafeSurface(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        Location location = new Location(world, x, y, z);
        if (isSafe(location)) {
            return location.add(0.5, 1, 0.5);
        }
        return null;
    }

    public static boolean isSafe(Location location) {
        if (location == null || location.getWorld() == null) return false;
        if (location.getY() < 64) return false;
        if (location.getY() > location.getWorld().getMaxHeight() - 10) return false;

        Block block = location.getBlock();
        Block below = location.clone().subtract(0, 1, 0).getBlock();
        Block above = location.clone().add(0, 1, 0).getBlock();

        if (!below.getType().isSolid()) return false;
        if (SOLID_BLOCKS.contains(block.getType())) return false;
        if (UNSAFE_BLOCKS.contains(block.getType())) return false;
        if (SOLID_BLOCKS.contains(above.getType())) return false;

        for (Entity entity : location.getWorld().getNearbyEntities(location, 3, 3, 3)) {
            if (entity instanceof Player) return false;
        }

        return true;
    }

    public static boolean isInWorldBounds(Location location) {
        World world = location.getWorld();
        if (world == null) return false;
        return location.getX() >= -world.getWorldBorder().getSize() / 2
                && location.getX() <= world.getWorldBorder().getSize() / 2
                && location.getZ() >= -world.getWorldBorder().getSize() / 2
                && location.getZ() <= world.getWorldBorder().getSize() / 2;
    }

    public static String getDirection(Player player, Location target) {
        Location playerLoc = player.getLocation();
        double dx = target.getX() - playerLoc.getX();
        double dz = target.getZ() - playerLoc.getZ();

        double angle = Math.toDegrees(Math.atan2(dz, dx));
        if (angle < 0) angle += 360;

        if (angle >= 337.5 || angle < 22.5) return "Est";
        if (angle < 67.5) return "Nord-Est";
        if (angle < 112.5) return "Nord";
        if (angle < 157.5) return "Nord-Ouest";
        if (angle < 202.5) return "Ouest";
        if (angle < 247.5) return "Sud-Ouest";
        if (angle < 292.5) return "Sud";
        return "Sud-Est";
    }

    public static Location findIslandCenter(Player player) {
        World world = player.getWorld();
        int maxRadius = 100;
        for (int r = 10; r <= maxRadius; r += 5) {
            for (int angle = 0; angle < 360; angle += 15) {
                double rad = Math.toRadians(angle);
                int x = (int) (player.getLocation().getX() + r * Math.cos(rad));
                int z = (int) (player.getLocation().getZ() + r * Math.sin(rad));
                Location surface = findSafeSurface(world, x, z);
                if (surface != null && hasIslandStructure(surface)) {
                    return surface;
                }
            }
        }
        return player.getLocation().add(0, 50, 0);
    }

    private static boolean hasIslandStructure(Location location) {
        Block center = location.getBlock();
        int solidCount = 0;
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = -3; y <= 0; y++) {
                    Block b = center.getRelative(x, y, z);
                    if (b.getType().isSolid()) {
                        solidCount++;
                    }
                }
            }
        }
        return solidCount >= 15;
    }
}
