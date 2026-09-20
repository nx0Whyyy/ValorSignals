package com.valorsky.skysignals.model;

import org.bukkit.Location;
import java.util.Objects;

/** Immutable public description of an event's destination or affected world. */
public record EventLocation(String world, String label, BlockPosition position, int radius) {
    public EventLocation {
        Objects.requireNonNull(world);
        Objects.requireNonNull(label);
        if (radius < 0 || (position == null && radius != 0)) throw new IllegalArgumentException("Invalid radius");
    }

    public static EventLocation at(Location location, String label, int radius) {
        return new EventLocation(Objects.requireNonNull(location.getWorld()).getName(), label,
                new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ()), radius);
    }

    public static EventLocation world(String world, String label) {
        return new EventLocation(world, label, null, 0);
    }

    public record BlockPosition(int x, int y, int z) {}
}
