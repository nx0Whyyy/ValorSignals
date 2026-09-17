package com.valorsky.skysignals.redis;

import com.valorsky.skysignals.model.EventState;
import com.valorsky.skysignals.model.SkyEventStatus;
import com.valorsky.skysignals.model.SkyEventType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RedisSerializationTest {

    private Gson gson;

    @BeforeEach
    void setUp() {
        gson = new GsonBuilder()
                .registerTypeAdapter(Instant.class, new InstantAdapter())
                .registerTypeAdapter(SkyEventType.class, new EnumAdapter<>(SkyEventType.class))
                .registerTypeAdapter(SkyEventStatus.class, new EnumAdapter<>(SkyEventStatus.class))
                .create();
    }

    @Test
    void testSerializeEventState() {
        EventState state = new EventState(
                UUID.randomUUID(), SkyEventType.METEOR, "SkyBlock-01",
                Instant.ofEpochSecond(1750000000), Instant.ofEpochSecond(1750000300),
                SkyEventStatus.ACTIVE, Map.of("key", "value")
        );

        String json = gson.toJson(state);
        assertNotNull(json);
        assertTrue(json.contains("METEOR"));
        assertTrue(json.contains("SkyBlock-01"));
        assertTrue(json.contains("ACTIVE"));
    }

    @Test
    void testDeserializeEventState() {
        String json = "{\"id\":\"550e8400-e29b-41d4-a716-446655440000\",\"type\":\"METEOR\",\"serverId\":\"SkyBlock-01\",\"startedAt\":1750000000,\"endsAt\":1750000300,\"status\":\"ACTIVE\",\"data\":{}}";

        EventState state = gson.fromJson(json, EventState.class);
        assertEquals(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), state.id());
        assertEquals(SkyEventType.METEOR, state.type());
        assertEquals("SkyBlock-01", state.serverId());
        assertEquals(Instant.ofEpochSecond(1750000000), state.startedAt());
        assertEquals(Instant.ofEpochSecond(1750000300), state.endsAt());
        assertEquals(SkyEventStatus.ACTIVE, state.status());
    }

    @Test
    void testSerializeDeserializeAllTypes() {
        for (SkyEventType type : SkyEventType.values()) {
            EventState state = new EventState(
                    UUID.randomUUID(), type, "server",
                    Instant.now(), Instant.now().plusSeconds(300),
                    SkyEventStatus.SCHEDULED, Map.of()
            );
            String json = gson.toJson(state);
            EventState deserialized = gson.fromJson(json, EventState.class);
            assertEquals(type, deserialized.type());
        }
    }

    @Test
    void testSerializeDeserializeAllStatuses() {
        for (SkyEventStatus status : SkyEventStatus.values()) {
            EventState state = new EventState(
                    UUID.randomUUID(), SkyEventType.METEOR, "server",
                    Instant.now(), Instant.now().plusSeconds(300),
                    status, Map.of()
            );
            String json = gson.toJson(state);
            EventState deserialized = gson.fromJson(json, EventState.class);
            assertEquals(status, deserialized.status());
        }
    }

    @Test
    void testInstantAdapter() {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        String json = gson.toJson(now);
        Instant deserialized = gson.fromJson(json, Instant.class);
        assertEquals(now, deserialized);
    }

    @Test
    void testWithStatusPreservesData() {
        EventState original = new EventState(
                UUID.randomUUID(), SkyEventType.METEOR, "server",
                Instant.now(), Instant.now().plusSeconds(300),
                SkyEventStatus.SCHEDULED, Map.of("custom", "data")
        );
        EventState updated = original.withStatus(SkyEventStatus.ACTIVE);
        assertEquals(SkyEventStatus.ACTIVE, updated.status());
        assertEquals("data", updated.data().get("custom"));
    }
}
