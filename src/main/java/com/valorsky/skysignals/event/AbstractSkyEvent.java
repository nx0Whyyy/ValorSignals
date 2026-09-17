package com.valorsky.skysignals.event;

import com.valorsky.skysignals.model.EventState;
import com.valorsky.skysignals.model.SkyEvent;
import com.valorsky.skysignals.model.SkyEventStatus;
import com.valorsky.skysignals.model.SkyEventType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.UUID;

public abstract class AbstractSkyEvent implements SkyEvent {

    protected final UUID id;
    protected final SkyEventType type;
    protected final String serverId;
    protected final Instant startedAt;
    protected final Instant endsAt;
    protected final JavaPlugin plugin;
    protected SkyEventStatus status;

    protected AbstractSkyEvent(EventState state, JavaPlugin plugin) {
        this.id = state.id();
        this.type = state.type();
        this.serverId = state.serverId();
        this.startedAt = state.startedAt();
        this.endsAt = state.endsAt();
        this.status = state.status();
        this.plugin = plugin;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public SkyEventType getType() {
        return type;
    }

    @Override
    public String getServerId() {
        return serverId;
    }

    @Override
    public Instant getStartedAt() {
        return startedAt;
    }

    @Override
    public Instant getEndsAt() {
        return endsAt;
    }

    @Override
    public SkyEventStatus getStatus() {
        return status;
    }

    @Override
    public void cancel() {
        this.status = SkyEventStatus.CANCELLED;
    }

    protected EventState toState() {
        return new EventState(id, type, serverId, startedAt, endsAt, status, getExtraData());
    }

    protected java.util.Map<String, Object> getExtraData() {
        return java.util.Map.of();
    }
}
