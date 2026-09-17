package com.valorsky.skysignals.event;

import com.valorsky.skysignals.model.EventCreator;
import com.valorsky.skysignals.model.EventState;
import com.valorsky.skysignals.model.SkyEvent;
import com.valorsky.skysignals.model.SkyEventType;
import com.valorsky.skysignals.model.SkyEventStatus;
import com.valorsky.skysignals.model.EventScope;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SkyEventFactory {

    private final Map<SkyEventType, EventCreator> creators = new ConcurrentHashMap<>();

    public void register(SkyEventType type, EventCreator creator) {
        creators.put(type, creator);
    }

    public Set<SkyEventType> getRegisteredTypes() {
        return Set.copyOf(creators.keySet());
    }

    public boolean isRegistered(SkyEventType type) {
        if (type == null) return false;
        return creators.containsKey(type);
    }

    public SkyEvent create(SkyEventType type, String serverId, Duration duration, EventContext context) {
        EventCreator creator = creators.get(type);
        if (creator == null) {
            throw new IllegalArgumentException("No creator registered for event type: " + type);
        }
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Instant end = now.plus(duration);
        EventState state = new EventState(
            id, type, serverId, EventScope.SERVER, now, now, end,
            SkyEventStatus.SCHEDULED, com.valorsky.skysignals.model.SkyEventPhase.SCHEDULED, 0, Map.of()
        );
        return creator.create(state, context);
    }

    public SkyEvent createFromState(EventState state, EventContext context) {
        EventCreator creator = creators.get(state.type());
        if (creator == null) {
            throw new IllegalArgumentException("No creator registered for event type: " + state.type());
        }
        return creator.create(state, context);
    }
}