package com.valorsky.skysignals.protection;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class DefaultProtectionProvider implements ProtectionProvider {

    @Override
    public boolean canModify(Location location) {
        return true;
    }

    @Override
    public boolean canInteract(Location location, Player player) {
        return true;
    }

    @Override
    public boolean canSpawnEntity(Location location) {
        return true;
    }

    @Override
    public boolean canPlaceBlock(Location location, Material material) {
        return true;
    }

    @Override
    public boolean canBreakBlock(Location location, Player player) {
        return true;
    }
}