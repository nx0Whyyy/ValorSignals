package com.valorsky.skysignals.particle;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.List;

public interface ParticleShape {
    void spawn(Location center, Particle particle, int count, double speed, List<Player> audience);
    String getName();
}