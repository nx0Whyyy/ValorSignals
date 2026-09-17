package com.valorsky.skysignals.protection;

import org.bukkit.Location;

public interface ProtectionProvider {
    boolean canModify(Location location);
    boolean canInteract(Location location, org.bukkit.entity.Player player);
    boolean canSpawnEntity(Location location);
    boolean canPlaceBlock(Location location, org.bukkit.Material material);
    boolean canBreakBlock(Location location, org.bukkit.entity.Player player);
}