package com.valorsky.skysignals.config;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ConfigRegressionTest {
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
