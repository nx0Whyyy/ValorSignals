package com.valorsky.skysignals.island;

import org.bukkit.Location;

import java.util.Optional;
import java.util.UUID;

public interface Island {
    UUID getId();
    String getName();
    Optional<Location> getCenter();
    double getRadius();
    boolean isMember(UUID playerId);
    boolean isOwner(UUID playerId);
}