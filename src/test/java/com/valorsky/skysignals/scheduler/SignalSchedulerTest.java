package com.valorsky.skysignals.scheduler;
import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.event.*;
import com.valorsky.skysignals.model.SkyEventType;
import com.valorsky.skysignals.redis.DistributedLockService;
import com.valorsky.skysignals.util.FoliaScheduler;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SignalSchedulerTest {
    @Test void standaloneServerStartsEventsAndReloadCancelsPreviousCheck() {
        try (var scheduling = mockStatic(FoliaScheduler.class, CALLS_REAL_METHODS)) {
            var plugin = mock(JavaPlugin.class);
            when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger()); when(plugin.isEnabled()).thenReturn(true);
            var config = mock(Config.class);
            when(config.schedulerEnabled()).thenReturn(true);
            when(config.schedulerMinInterval()).thenReturn(1); when(config.schedulerMaxInterval()).thenReturn(1);
            when(config.eventsMaxActive()).thenReturn(1);
            when(config.getAllowedSchedulerEvents()).thenReturn(Set.of(SkyEventType.STORM));
            when(config.isEventEnabled(SkyEventType.STORM)).thenReturn(true);
            when(config.getEventWeights()).thenReturn(Map.of(SkyEventType.STORM, 1));
            when(config.getEventConflicts()).thenReturn(Map.of());
            var manager = mock(SkyEventManager.class); var context = mock(EventContext.class);
            when(manager.startEvent(SkyEventType.STORM, context)).thenReturn(CompletableFuture.completedFuture(null));
            var lock = mock(DistributedLockService.class);
            var pending = new ArrayList<Runnable>();
            var handle = mock(FoliaScheduler.TaskHandle.class);
            scheduling.when(() -> FoliaScheduler.runGlobalDelayed(eq(plugin), any(), anyLong())).thenAnswer(inv -> {
                pending.add(inv.getArgument(1)); return handle;
            });
            scheduling.when(() -> FoliaScheduler.runGlobal(eq(plugin), any())).thenAnswer(inv -> { ((Runnable) inv.getArgument(1)).run(); return null; });
            var scheduler = new SignalScheduler(plugin, config, manager, lock, context);
            scheduler.start(); assertEquals(1, pending.size());
            pending.getFirst().run();
            verify(manager).startEvent(SkyEventType.STORM, context);
            verify(lock, never()).tryLock(anyString(), any(), anyString());
            scheduler.reload();
            verify(handle, atLeastOnce()).cancel();
            scheduler.stop(); assertFalse(scheduler.isRunning());
        }
    }
}
