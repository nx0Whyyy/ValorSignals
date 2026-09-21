package com.valorsky.skysignals.notification;

import com.valorsky.skysignals.config.*;
import com.valorsky.skysignals.event.impl.*;
import com.valorsky.skysignals.location.SafeLocationService;
import com.valorsky.skysignals.model.*;
import com.valorsky.skysignals.particle.ParticleService;
import com.valorsky.skysignals.reward.RewardService;
import com.valorsky.skysignals.sound.SoundService;
import com.valorsky.skysignals.util.FoliaScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventLocationTest {
    @Test
    void meteorTestUsesTheCommandPlayersExactLocation() {
        var plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        var world = mock(World.class);
        when(world.getName()).thenReturn("test_world");
        var locations = mock(SafeLocationService.class);
        var sound = mock(SoundService.class);
        var now = Instant.now();
        var state = new EventState(UUID.randomUUID(), SkyEventType.METEOR, "test", EventScope.SERVER,
                now, now, now.plusSeconds(180), SkyEventStatus.SCHEDULED,
                SkyEventPhase.SCHEDULED, 0, Map.of());
        var meteor = new MeteorEvent(state, plugin, mock(NotificationService.class),
                mock(RewardService.class), mock(ParticleService.class), sound, locations, mock(Config.class));

        meteor.setForcedTarget(new Location(world, 42.8, 75, -18.2));
        meteor.onPhaseChange(SkyEventPhase.ANNOUNCING);

        var target = meteor.getImpactLocation().orElseThrow();
        assertEquals(42.8, target.getX());
        assertEquals(75, target.getY());
        assertEquals(-18.2, target.getZ());
        verifyNoInteractions(locations);
        verify(sound).playGlobal("meteor_start");
    }

    @ParameterizedTest
    @EnumSource(value = SkyEventType.class, names = {"METEOR", "SKY_CHEST", "MOB_INVASION", "MINERAL_RAIN"})
    void exposesTheActualPreparedDestination(SkyEventType type) {
        var plugin = mock(JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        var world = mock(World.class); when(world.getName()).thenReturn("skyblock");
        var target = new Location(world, -12.8, 71, 325.9);
        var locations = mock(SafeLocationService.class);
        when(locations.findNearPlayersAsync(anyDouble(), anyDouble())).thenReturn(CompletableFuture.completedFuture(Optional.of(target)));
        var notifications = mock(NotificationService.class);
        var particles = mock(ParticleService.class); var sound = mock(SoundService.class);
        var rewards = mock(RewardService.class); var config = mock(Config.class);
        var now = Instant.now();
        var state = new EventState(UUID.randomUUID(), type, "test", EventScope.SERVER, now, now, now.plusSeconds(180), SkyEventStatus.SCHEDULED, SkyEventPhase.SCHEDULED, 0, Map.of());
        SkyEvent event = switch (type) {
            case METEOR -> new MeteorEvent(state, plugin, notifications, rewards, particles, sound, locations, config);
            case SKY_CHEST -> new SkyChestEvent(state, plugin, notifications, rewards, particles, sound, locations, config);
            case MOB_INVASION -> new MobInvasionEvent(state, plugin, notifications, rewards, particles, sound, locations, config);
            case MINERAL_RAIN -> new MineralRainEvent(state, plugin, notifications, rewards, particles, sound, locations, config);
            default -> throw new AssertionError();
        };
        assertTrue(event.getEventLocations().isEmpty());
        try (var scheduler = mockStatic(FoliaScheduler.class)) {
            scheduler.when(() -> FoliaScheduler.runGlobal(eq(plugin), any())).thenAnswer(call -> { ((Runnable) call.getArgument(1)).run(); return null; });
            event.onPhaseChange(SkyEventPhase.ANNOUNCING);
        }
        var location = event.getEventLocations().getFirst();
        assertEquals("skyblock", location.world());
        assertEquals(new EventLocation.BlockPosition(-13, 71, 325), location.position());
        assertEquals(type == SkyEventType.MOB_INVASION ? 15 : type == SkyEventType.MINERAL_RAIN ? 20 : 0, location.radius());
        target.add(100, 0, 0);
        assertEquals(-13, location.position().x());
    }

    @Test void chatAnnouncesLocationOnceAndActiveCommandCanAlwaysDisplayIt() {
        var plugin = mock(JavaPlugin.class); var server = mock(Server.class); var player = mock(Player.class);
        when(plugin.getServer()).thenReturn(server);
        doReturn(List.of(player)).when(server).getOnlinePlayers();
        var config = mock(Config.class); when(config.notificationsChat()).thenReturn(true);
        var messages = mock(MessageConfig.class);
        when(messages.get(anyString())).thenReturn("<gold>Événement</gold>");
        when(messages.get(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        var service = new NotificationService(plugin, config, messages, mock(TitleService.class), mock(ActionBarService.class), mock(BossBarService.class), mock(SoundService.class));
        var event = mock(SkyEvent.class);
        when(event.getServerId()).thenReturn("test");
        when(event.getId()).thenReturn(UUID.randomUUID()); when(event.getType()).thenReturn(SkyEventType.MINERAL_RAIN);
        when(event.getEventLocations()).thenReturn(List.of(new EventLocation("skyblock", "Zone de retombée", new EventLocation.BlockPosition(-13, 71, 325), 20)));
        service.notifyEventPhase(event, "start"); service.notifyEventPhase(event, "active");
        var output = org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(player, times(2)).sendMessage(output.capture());
        var plain = PlainTextComponentSerializer.plainText();
        assertTrue(plain.serialize(output.getAllValues().get(0)).contains("Monde : skyblock"));
        assertFalse(plain.serialize(output.getAllValues().get(1)).contains("Monde :"));
        String description = plain.serialize(service.describeLocation(event));
        assertTrue(description.contains("X : -13"));
        assertTrue(description.contains("Rayon : 20 blocs"));
        assertFalse(description.contains("%"));
        when(event.getEventLocations()).thenReturn(List.of(EventLocation.world("world_nether", "Cultures dans tout le monde")));
        description = plain.serialize(service.describeLocation(event));
        assertTrue(description.contains("world_nether")); assertFalse(description.contains("X :"));
        when(event.getEventLocations()).thenReturn(List.of());
        assertTrue(plain.serialize(service.describeLocation(event)).contains("en cours de recherche"));
    }

    @Test void globalEventsReportAffectedWorldsWithoutInventedCoordinates() {
        var plugin = mock(JavaPlugin.class); var server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server); when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        var overworld = mock(World.class); when(overworld.getName()).thenReturn("islands");
        var nether = mock(World.class); when(nether.getName()).thenReturn("islands_nether");
        when(server.getWorlds()).thenReturn(List.of(overworld, nether));
        var now = Instant.now(); var notifications = mock(NotificationService.class);
        var particles = mock(ParticleService.class); var sound = mock(SoundService.class);
        var state = new EventState(UUID.randomUUID(), SkyEventType.GROWTH_BOOST, "test", EventScope.SERVER, now, now, now.plusSeconds(180), SkyEventStatus.SCHEDULED, SkyEventPhase.SCHEDULED, 0, Map.of());
        var growth = new GrowthBoostEvent(state, plugin, notifications, particles, sound, mock(Config.class));
        assertEquals(List.of("islands", "islands_nether"), growth.getEventLocations().stream().map(EventLocation::world).toList());
        assertTrue(growth.getEventLocations().stream().allMatch(loc -> loc.position() == null));
        var storm = new StormEvent(state, plugin, notifications, mock(RewardService.class), particles, sound);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(overworld);
            storm.onPhaseChange(SkyEventPhase.ANNOUNCING);
        }
        assertEquals("islands", storm.getEventLocations().getFirst().world());
        assertNull(storm.getEventLocations().getFirst().position());
    }
}
