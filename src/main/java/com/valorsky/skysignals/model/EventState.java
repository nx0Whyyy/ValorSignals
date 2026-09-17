package com.valorsky.skysignals.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record EventState(
    UUID id,
    SkyEventType type,
    String serverId,
    Instant startedAt,
    Instant endsAt,
    SkyEventStatus status,
    Map<String, Object> data
) {

    public EventState {
        if (data == null) {
            data = Map.of();
        }
    }

    public EventState(UUID id, SkyEventType type, String serverId, Instant startedAt, Instant endsAt, SkyEventStatus status) {
        this(id, type, serverId, startedAt, endsAt, status, Map.of());
    }

    public EventState withStatus(SkyEventStatus newStatus) {
        return new EventState(id, type, serverId, startedAt, endsAt, newStatus, data);
    }

    public EventState withData(Map<String, Object> newData) {
        Map<String, Object> merged = new HashMap<>(this.data);
        if (newData != null) {
            merged.putAll(newData);
        }
        return new EventState(id, type, serverId, startedAt, endsAt, status, merged);
    }
}
