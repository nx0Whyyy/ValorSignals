package com.valorsky.skysignals.config;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ConfigRegressionTest {
    @Test void meteorDefaultsAndFormerValuesUpgradeToTheCompleteCrater() {
        var yaml = new YamlConfiguration();
        yaml.set("events-config.meteor.model.scale", 5.0);
        yaml.set("events-config.meteor.model.descent-duration", 8);
        yaml.set("events-config.meteor.crater.radius", 5);
        var plugin = mock(JavaPlugin.class); when(plugin.getConfig()).thenReturn(yaml);
        var config = new Config(plugin); config.load();
        assertEquals(3.0f, config.getMeteorModelScale());
        assertEquals(5, config.getMeteorDescentDuration());
        assertEquals(8, config.getMeteorCraterRadius());
        assertTrue(config.meteorRestorationEnabled());
        assertEquals(20, config.getMeteorRestorationDelayMinutes());
        assertFalse(config.getMeteorChestLoot().isEmpty());
        assertEquals(3, config.getMeteorMinimumChests());
        assertEquals(12, config.getMeteorMaximumChests());
        assertEquals(7, config.getMeteorMaximumHiddenDepth());
        assertEquals(2.0, config.getMeteorMainLootMultiplier());
    }

    @Test void eventSettingsUseTheShippedUnderscoreKeys() {
        var yaml = new YamlConfiguration();
        yaml.set("events-config.mob_invasion.max-mobs", 7);
        yaml.set("events-config.mineral_rain.spawn-interval", 3);
        yaml.set("events-config.growth_boost.multiplier", 4.0);
        var plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(yaml);
        var config = new Config(plugin); config.load();
        assertEquals(7, config.getMobInvasionMaxMobs());
        assertEquals(3, config.getMineralRainSpawnInterval());
        assertEquals(4.0, config.getGrowthBoostMultiplier());
    }
    @Test void invertedIntervalsAndZeroPeriodsCannotBreakScheduling() {
        var yaml = new YamlConfiguration();
        yaml.set("scheduler.min-interval", 50); yaml.set("scheduler.max-interval", 10);
        yaml.set("events-config.mineral_rain.spawn-interval", 0);
        var plugin = mock(JavaPlugin.class); when(plugin.getConfig()).thenReturn(yaml);
        var config = new Config(plugin); config.load();
        assertEquals(50, config.schedulerMaxInterval());
        assertEquals(1, config.getMineralRainSpawnInterval());
    }
}
