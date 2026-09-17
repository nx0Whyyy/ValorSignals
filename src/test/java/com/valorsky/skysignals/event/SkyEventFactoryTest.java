package com.valorsky.skysignals.event;

import com.valorsky.skysignals.model.EventState;
import com.valorsky.skysignals.model.SkyEvent;
import com.valorsky.skysignals.model.SkyEventStatus;
import com.valorsky.skysignals.model.SkyEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SkyEventFactoryTest {

    private SkyEventFactory factory;
    private SkyEvent testEvent;

    @BeforeEach
    void setUp() {
        factory = new SkyEventFactory();

        factory.register(SkyEventType.METEOR, state -> new TestSkyEvent(state));
        factory.register(SkyEventType.STORM, state -> new TestSkyEvent(state));
        factory.register(SkyEventType.SKY_CHEST, state -> new TestSkyEvent(state));
        factory.register(SkyEventType.MOB_INVASION, state -> new TestSkyEvent(state));
        factory.register(SkyEventType.MINERAL_RAIN, state -> new TestSkyEvent(state));
        factory.register(SkyEventType.GROWTH_BOOST, state -> new TestSkyEvent(state));
    }

    @Test
    void testCreateMeteorEvent() {
        SkyEvent event = factory.create(SkyEventType.METEOR, "test-server", Duration.ofSeconds(180));
        assertEquals(SkyEventType.METEOR, event.getType());
        assertNotNull(event.getId());
        assertEquals("test-server", event.getServerId());
        assertEquals(SkyEventStatus.SCHEDULED, event.getStatus());
        assertEquals(180, Duration.between(event.getStartedAt(), event.getEndsAt()).getSeconds());
    }

    @Test
    void testCreateAllEventTypes() {
        for (SkyEventType type : SkyEventType.values()) {
            SkyEvent event = factory.create(type, "test-server", Duration.ofSeconds(60));
            assertEquals(type, event.getType());
        }
    }

    @Test
    void testCreateFromState() {
        SkyEvent original = factory.create(SkyEventType.METEOR, "test-server", Duration.ofSeconds(180));
        EventState state = new EventState(
                original.getId(),
                original.getType(),
                original.getServerId(),
                original.getStartedAt(),
                original.getEndsAt(),
                SkyEventStatus.ACTIVE,
                java.util.Map.of()
        );

        SkyEvent restored = factory.createFromState(state);
        assertEquals(original.getId(), restored.getId());
        assertEquals(SkyEventType.METEOR, restored.getType());
        assertEquals(SkyEventStatus.ACTIVE, restored.getStatus());
    }

    @Test
    void testUnregisteredTypeThrows() {
        SkyEventFactory newFactory = new SkyEventFactory();
        assertThrows(IllegalArgumentException.class, () ->
                newFactory.create(SkyEventType.METEOR, "test", Duration.ofSeconds(60))
        );
    }

    @Test
    void testRegisteredTypes() {
        assertEquals(6, new java.util.HashSet<>(factory.getRegisteredTypes()).size());
        assertTrue(factory.isRegistered(SkyEventType.METEOR));
        assertFalse(factory.isRegistered(null));
    }

    private static class TestSkyEvent implements SkyEvent {
        private final UUID id;
        private final SkyEventType type;
        private final String serverId;
        private final Instant startedAt;
        private final Instant endsAt;
        private SkyEventStatus status;

        TestSkyEvent(EventState state) {
            this.id = state.id();
            this.type = state.type();
            this.serverId = state.serverId();
            this.startedAt = state.startedAt();
            this.endsAt = state.endsAt();
            this.status = state.status();
        }

        @Override public UUID getId() { return id; }
        @Override public SkyEventType getType() { return type; }
        @Override public String getServerId() { return serverId; }
        @Override public Instant getStartedAt() { return startedAt; }
        @Override public Instant getEndsAt() { return endsAt; }
        @Override public SkyEventStatus getStatus() { return status; }
        @Override public void start() { status = SkyEventStatus.ACTIVE; }
        @Override public void tick() {}
        @Override public void stop() { status = SkyEventStatus.FINISHED; }
        @Override public void cancel() { status = SkyEventStatus.CANCELLED; }
    }
}
