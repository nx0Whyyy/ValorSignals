package com.valorsky.skysignals.util;
import io.papermc.paper.threadedregions.scheduler.*;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoliaSchedulerTest {
    @Test void zeroDelayIsAcceptedAndCancellationReachesServer() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            var scheduler = mock(GlobalRegionScheduler.class);
            var plugin = mock(JavaPlugin.class);
            var task = mock(ScheduledTask.class);
            bukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(scheduler);
            when(scheduler.runAtFixedRate(eq(plugin), any(), eq(1L), eq(2L))).thenReturn(task);
            var handle = FoliaScheduler.runGlobalTimer(plugin, () -> {}, 0, 2);
            handle.cancel();
            verify(task).cancel();
            bukkit.verify(Bukkit::getScheduler, never());
        }
    }
    @Test void locationTaskNeverFallsBackToGlobalThread() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            var scheduler = mock(RegionScheduler.class);
            var plugin = mock(JavaPlugin.class);
            var location = new Location(mock(World.class), 12, 64, 12);
            Runnable action = mock(Runnable.class);
            bukkit.when(Bukkit::getRegionScheduler).thenReturn(scheduler);
            FoliaScheduler.runRegion(plugin, location, action);
            verify(scheduler).execute(plugin, location, action);
            bukkit.verify(Bukkit::getGlobalRegionScheduler, never());
            bukkit.verify(Bukkit::getScheduler, never());
        }
    }
    @Test void entityTasksFollowEntityScheduler() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            var entity = mock(Entity.class);
            var scheduler = mock(EntityScheduler.class);
            var plugin = mock(JavaPlugin.class);
            when(entity.getScheduler()).thenReturn(scheduler);
            FoliaScheduler.runEntity(plugin, entity, () -> {});
            verify(scheduler).run(eq(plugin), any(), isNull());
            bukkit.verify(Bukkit::getGlobalRegionScheduler, never());
        }
    }
    @Test void asyncDelayConvertsTicksToMilliseconds() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            var scheduler = mock(AsyncScheduler.class);
            var plugin = mock(JavaPlugin.class);
            bukkit.when(Bukkit::getAsyncScheduler).thenReturn(scheduler);
            FoliaScheduler.runAsyncDelayed(plugin, () -> {}, 100);
            verify(scheduler).runDelayed(eq(plugin), any(), eq(5000L), eq(TimeUnit.MILLISECONDS));
        }
    }
    @Test void futureWaitsForExecutionAndPropagatesFailure() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            var scheduler = mock(GlobalRegionScheduler.class);
            bukkit.when(Bukkit::getGlobalRegionScheduler).thenReturn(scheduler);
            var plugin = mock(JavaPlugin.class);
            var future = FoliaScheduler.supplyGlobal(plugin, () -> { throw new IllegalStateException("failed"); });
            assertFalse(future.isDone());
            var task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).execute(eq(plugin), task.capture());
            task.getValue().run();
            assertTrue(future.isCompletedExceptionally());
        }
    }
}
