package com.valorsky.skysignals.cache;

import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.model.EventState;
import com.valorsky.skysignals.model.SkyEventStatus;
import com.valorsky.skysignals.model.SkyEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CacheServiceTest {

    private CacheService cache;

    @BeforeEach
    void setUp() {
        Config mockConfig = new Config(null) {
            @Override public int cacheMaximumSize() { return 10; }
            @Override public int cacheExpireAfterMinutes() { return 1; }
        };
        this.cache = new CacheService(mockConfig);
    }

    @Test
    void testPutAndGet() {
        EventState state = createTestState();
        cache.put(state.id().toString(), state);

        EventState retrieved = cache.get(state.id().toString());
        assertNotNull(retrieved);
        assertEquals(state.id(), retrieved.id());
        assertEquals(SkyEventType.METEOR, retrieved.type());
    }

    @Test
    void testGetIfPresentReturnsNullForMissing() {
        assertNull(cache.get("nonexistent-key"));
    }

    @Test
    void testRemove() {
        EventState state = createTestState();
        cache.put(state.id().toString(), state);
        assertNotNull(cache.get(state.id().toString()));

        cache.remove(state.id().toString());
        assertNull(cache.get(state.id().toString()));
    }

    @Test
    void testSize() {
        assertEquals(0, cache.size());
        cache.put(createTestState().id().toString(), createTestState());
        cache.put(createTestState().id().toString(), createTestState());
        cache.put(createTestState().id().toString(), createTestState());
        assertEquals(3, cache.size());
    }

    @Test
    void testGetAll() {
        cache.put(createTestState().id().toString(), createTestState());
        cache.put(createTestState().id().toString(), createTestState());
        assertFalse(cache.getAll().isEmpty());
        assertEquals(2, cache.getAll().size());
    }

    @Test
    void testGetActiveEvents() {
        EventState active = new EventState(
                UUID.randomUUID(), SkyEventType.METEOR, "server1",
                Instant.now(), Instant.now().plusSeconds(120),
                SkyEventStatus.ACTIVE, Map.of()
        );
        EventState finished = new EventState(
                UUID.randomUUID(), SkyEventType.STORM, "server1",
                Instant.now(), Instant.now().plusSeconds(120),
                SkyEventStatus.FINISHED, Map.of()
        );
        cache.put(active.id().toString(), active);
        cache.put(finished.id().toString(), finished);

        var activeEvents = cache.getActiveEvents("server1");
        assertEquals(1, activeEvents.size());
        assertEquals(SkyEventType.METEOR, activeEvents.get(0).type());
    }

    @Test
    void testClear() {
        cache.put(createTestState().id().toString(), createTestState());
        cache.put(createTestState().id().toString(), createTestState());
        assertEquals(2, cache.size());

        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    void testStats() {
        cache.put(createTestState().id().toString(), createTestState());
        cache.get("nonexistent");

        Map<String, Object> stats = cache.getStats();
        assertEquals(1L, stats.get("missCount"));
        assertEquals(0L, stats.get("hitCount"));
    }

    private EventState createTestState() {
        return new EventState(
                UUID.randomUUID(), SkyEventType.METEOR, "test-server",
                Instant.now(), Instant.now().plusSeconds(300),
                SkyEventStatus.ACTIVE, Map.of()
        );
    }
}
