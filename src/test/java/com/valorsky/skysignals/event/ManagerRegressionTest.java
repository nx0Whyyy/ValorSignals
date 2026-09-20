package com.valorsky.skysignals.event;
import com.valorsky.skysignals.cache.*;
import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.database.EventRepository;
import com.valorsky.skysignals.model.*;
import com.valorsky.skysignals.notification.NotificationService;
import com.valorsky.skysignals.rabbitmq.EventPublisher;
import com.valorsky.skysignals.redis.RedisService;
import com.valorsky.skysignals.util.FoliaScheduler;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagerRegressionTest {
    static class RealLifecycleEvent extends AbstractSkyEvent {
        int stops;
        RealLifecycleEvent(JavaPlugin plugin, Instant started, Instant end) {
            super(new EventState(UUID.randomUUID(), SkyEventType.GROWTH_BOOST, "test", EventScope.SERVER,
                started, started, end, SkyEventStatus.SCHEDULED, SkyEventPhase.SCHEDULED, 0, Map.of()), plugin);
        }
        public void stop() { stops++; }
    }
    @Test void advancesRealEventBeyondAnnouncementAndCleansTerminalState() {
        try (var scheduler = mockStatic(FoliaScheduler.class)) {
            var plugin = mock(JavaPlugin.class); when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            var config = mock(Config.class); when(config.getEventWarningDuration(any())).thenReturn(1);
            var manager = new SkyEventManager(plugin, new SkyEventFactory(), mock(CacheService.class),
                mock(LocalEventCache.class), mock(RedisService.class), mock(EventPublisher.class),
                mock(EventRepository.class), mock(NotificationService.class), config);
            manager.initialize();
            var event = new RealLifecycleEvent(plugin, Instant.now().minusSeconds(5), Instant.now().plusSeconds(60));
            manager.startEvent(event);
            assertEquals(SkyEventPhase.ANNOUNCING, event.getPhase());
            manager.tick(); assertEquals(SkyEventPhase.WARNING, event.getPhase());
            manager.tick(); assertEquals(SkyEventPhase.ACTIVE, event.getPhase());
            manager.finishEvent(event, false);
            assertEquals(SkyEventStatus.CANCELLED, event.getStatus());
            assertFalse(manager.isEventActive());
            assertEquals(1, event.stops);
            manager.finishEvent(event, false); assertEquals(1, event.stops);
        }
    }
    @Test void selfCancellationDoesNotLeaveEventOccupyingAnActiveSlot() {
        try (var scheduler = mockStatic(FoliaScheduler.class)) {
            var plugin = mock(JavaPlugin.class); when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
            var manager = new SkyEventManager(plugin, new SkyEventFactory(), mock(CacheService.class),
                mock(LocalEventCache.class), mock(RedisService.class), mock(EventPublisher.class),
                mock(EventRepository.class), mock(NotificationService.class), mock(Config.class));
            manager.initialize();
            var event = new RealLifecycleEvent(plugin, Instant.now(), Instant.now().plusSeconds(60));
            manager.startEvent(event); event.cancel(); manager.tick();
            assertFalse(manager.isEventActive()); assertEquals(1, event.stops);
        }
    }
    @Test void meteorCanBeSerializedBeforeTargetIsPrepared() {
        var plugin = mock(JavaPlugin.class); when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        var now = Instant.now();
        AbstractSkyEvent event = new com.valorsky.skysignals.event.impl.MeteorEvent(
            new EventState(UUID.randomUUID(), SkyEventType.METEOR, "test", EventScope.SERVER,
                now, now, now.plusSeconds(60), SkyEventStatus.SCHEDULED, SkyEventPhase.SCHEDULED, 0, Map.of()),
            plugin, mock(NotificationService.class), mock(com.valorsky.skysignals.reward.RewardService.class),
            mock(com.valorsky.skysignals.particle.ParticleService.class), mock(com.valorsky.skysignals.sound.SoundService.class),
            mock(com.valorsky.skysignals.location.SafeLocationService.class), mock(Config.class));
        assertDoesNotThrow(event::getExtraData);
        assertNotNull(event.getExtraData().get("direction"));
    }

    @Test void completingEventStillExpires() {
        var event = new RealLifecycleEvent(mock(JavaPlugin.class), Instant.now().minusSeconds(20), Instant.now().minusSeconds(1));
        event.onPhaseChange(SkyEventPhase.COMPLETING);
        assertTrue(event.isExpired());
    }
}
