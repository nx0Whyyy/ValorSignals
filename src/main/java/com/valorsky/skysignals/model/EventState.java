package com.valorsky.skysignals.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record EventState(
    UUID id,
    SkyEventType type,
    String serverId,
    EventScope scope,
    Instant scheduledAt,
    Instant startedAt,
    Instant endsAt,
    SkyEventStatus status,
    SkyEventPhase phase,
    long elapsedSeconds,
    Map<String, Object> data
) {

    public EventState {
        if (data == null) {
            data = Map.of();
        }
        if (scope == null) {
            scope = EventScope.SERVER;
        }
        if (phase == null) {
            phase = SkyEventPhase.SCHEDULED;
        }
        if (elapsedSeconds < 0) {
            elapsedSeconds = 0;
        }
    }

    public EventState(UUID id, SkyEventType type, String serverId, Instant startedAt, Instant endsAt, SkyEventStatus status) {
        this(id, type, serverId, EventScope.SERVER, Instant.now(), startedAt, endsAt, status, SkyEventPhase.SCHEDULED, 0, Map.of());
    }

    public EventState(UUID id, SkyEventType type, String serverId, Instant startedAt, Instant endsAt, SkyEventStatus status, Map<String, Object> data) {
        this(id, type, serverId, EventScope.SERVER, Instant.now(), startedAt, endsAt, status, SkyEventPhase.SCHEDULED, 0, data);
    }

    public EventState withStatus(SkyEventStatus newStatus) {
        return new EventState(id, type, serverId, scope, scheduledAt, startedAt, endsAt, newStatus, phase, elapsedSeconds, data);
    }

    public EventState withPhase(SkyEventPhase newPhase) {
        return new EventState(id, type, serverId, scope, scheduledAt, startedAt, endsAt, status, newPhase, elapsedSeconds, data);
    }

    public EventState withElapsedSeconds(long newElapsed) {
        return new EventState(id, type, serverId, scope, scheduledAt, startedAt, endsAt, status, phase, newElapsed, data);
    }

    public EventState withData(Map<String, Object> newData) {
        Map<String, Object> merged = new HashMap<>(this.data);
        if (newData != null) {
            merged.putAll(newData);
        }
        return new EventState(id, type, serverId, scope, scheduledAt, startedAt, endsAt, status, phase, elapsedSeconds, merged);
    }

    public boolean isActive() {
        return status == SkyEventStatus.ACTIVE;
    }

    public boolean isFinished() {
        return status == SkyEventStatus.FINISHED || status == SkyEventStatus.CANCELLED;
    }
}