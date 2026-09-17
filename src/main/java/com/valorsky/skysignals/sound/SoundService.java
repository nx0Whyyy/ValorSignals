package com.valorsky.skysignals.sound;

import com.valorsky.skysignals.config.Config;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SoundService {

    private final JavaPlugin plugin;
    private final Config config;
    private final Map<String, SoundConfig> soundConfigs = new ConcurrentHashMap<>();

    public SoundService(JavaPlugin plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
        registerDefaults();
    }

    private void registerDefaults() {
        soundConfigs.put("meteor_start", new SoundConfig(Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f));
        soundConfigs.put("meteor_warning", new SoundConfig(Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f));
        soundConfigs.put("meteor_impact", new SoundConfig(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f));
        soundConfigs.put("meteor_finish", new SoundConfig(Sound.ENTITY_ENDER_DRAGON_DEATH, 0.8f, 1.2f));

        soundConfigs.put("storm_start", new SoundConfig(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f));
        soundConfigs.put("storm_tick", new SoundConfig(Sound.BLOCK_NOTE_BLOCK_BASS, 0.3f, 0.5f));
        soundConfigs.put("storm_finish", new SoundConfig(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 0.8f));

        soundConfigs.put("sky_chest_descend", new SoundConfig(Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.0f));
        soundConfigs.put("sky_chest_land", new SoundConfig(Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f));
        soundConfigs.put("sky_chest_unlock", new SoundConfig(Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f));
        soundConfigs.put("sky_chest_claim", new SoundConfig(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f));

        soundConfigs.put("mob_invasion_start", new SoundConfig(Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.6f));
        soundConfigs.put("mob_invasion_wave", new SoundConfig(Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.8f));
        soundConfigs.put("mob_invasion_finish", new SoundConfig(Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f));

        soundConfigs.put("mineral_rain_start", new SoundConfig(Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f));
        soundConfigs.put("mineral_rain_drop", new SoundConfig(Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f));
        soundConfigs.put("mineral_rain_finish", new SoundConfig(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f));

        soundConfigs.put("growth_boost_start", new SoundConfig(Sound.ENTITY_VILLAGER_WORK_FARMER, 1.0f, 1.2f));
        soundConfigs.put("growth_boost_tick", new SoundConfig(Sound.BLOCK_GRASS_BREAK, 0.2f, 1.5f));
        soundConfigs.put("growth_boost_finish", new SoundConfig(Sound.ENTITY_VILLAGER_WORK_FARMER, 0.8f, 1.0f));

        soundConfigs.put("global_announce", new SoundConfig(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f));
        soundConfigs.put("warning", new SoundConfig(Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.7f));
        soundConfigs.put("success", new SoundConfig(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f));
        soundConfigs.put("failure", new SoundConfig(Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f));
    }

    public void play(Sound sound, Location location, List<Player> audience, float volume, float pitch) {
        if (!config.soundsEnabled()) return;
        for (Player player : audience) {
            if (player.getWorld().equals(location.getWorld()) &&
                player.getLocation().distanceSquared(location) <= config.viewDistance() * config.viewDistance()) {
                player.playSound(location, sound, volume, pitch);
            }
        }
    }

    public void play(String soundKey, Location location, List<Player> audience) {
        SoundConfig sc = soundConfigs.get(soundKey);
        if (sc != null) {
            play(sc.sound(), location, audience, sc.volume(), sc.pitch());
        }
    }

    public void playGlobal(String soundKey) {
        SoundConfig sc = soundConfigs.get(soundKey);
        if (sc != null) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                player.playSound(player.getLocation(), sc.sound(), sc.volume(), sc.pitch());
            }
        }
    }

    public void registerSound(String key, Sound sound, float volume, float pitch) {
        soundConfigs.put(key, new SoundConfig(sound, volume, pitch));
    }

    public record SoundConfig(Sound sound, float volume, float pitch) {}
}