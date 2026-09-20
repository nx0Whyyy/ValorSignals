package com.valorsky.skysignals.event;

import com.valorsky.skysignals.model.EventState;
import com.valorsky.skysignals.model.EventScope;
import com.valorsky.skysignals.model.SkyEvent;
import com.valorsky.skysignals.model.SkyEventPhase;
import com.valorsky.skysignals.model.SkyEventStatus;
import com.valorsky.skysignals.model.SkyEventType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public abstract class AbstractSkyEvent implements SkyEvent {

    protected final UUID id;
    protected final SkyEventType type;
    protected final String serverId;
    protected final EventScope scope;
    protected final Instant scheduledAt;
    protected Instant startedAt;
    protected final Instant endsAt;
    protected final JavaPlugin plugin;
    protected volatile SkyEventStatus status;
    protected volatile SkyEventPhase phase;
    protected long elapsedSeconds;
    protected final Random random = new Random();
    protected volatile boolean cancelled = false;

    protected AbstractSkyEvent(EventState state, JavaPlugin plugin) {
        this.id = state.id();
        this.type = state.type();
        this.serverId = state.serverId();
        this.scope = state.scope();
        this.scheduledAt = state.scheduledAt();
        this.startedAt = state.startedAt();
        this.endsAt = state.endsAt();
        this.status = state.status();
        this.phase = state.phase();
        this.elapsedSeconds = state.elapsedSeconds();
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
    public EventScope getScope() {
        return scope;
    }

    @Override
    public Instant getScheduledAt() {
        return scheduledAt;
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
    public SkyEventPhase getPhase() {
        return phase;
    }

    @Override
    public long getElapsedSeconds() {
        return elapsedSeconds;
    }

    @Override
    public void start() {
        this.status = SkyEventStatus.SCHEDULED;
        this.phase = SkyEventPhase.SCHEDULED;
        this.startedAt = Instant.now();
    }

    @Override
    public void onPhaseChange(SkyEventPhase newPhase) {
        if (newPhase == SkyEventPhase.CANCELLED) this.cancelled = true;
        this.phase = newPhase;
        this.status = phaseToStatus(newPhase);
        switch (newPhase) {
            case SCHEDULED -> onScheduled();
            case ANNOUNCING -> onAnnouncing();
            case WARNING -> onWarning();
            case ACTIVE -> onActive();
            case COMPLETING -> onCompleting();
            case FINISHED -> onFinished();
            case CANCELLED -> onCancelled();
        }
    }

    protected void onScheduled() {}
    protected void onAnnouncing() {}
    protected void onWarning() {}
    protected void onActive() {}
    protected void onCompleting() {}
    protected void onFinished() {}
    protected void onCancelled() {}

    private SkyEventStatus phaseToStatus(SkyEventPhase phase) {
        return switch (phase) {
            case SCHEDULED -> SkyEventStatus.SCHEDULED;
            case ANNOUNCING -> SkyEventStatus.ANNOUNCING;
            case WARNING -> SkyEventStatus.WARNING;
            case ACTIVE -> SkyEventStatus.ACTIVE;
            case COMPLETING -> SkyEventStatus.COMPLETING;
            case FINISHED -> SkyEventStatus.FINISHED;
            case CANCELLED -> SkyEventStatus.CANCELLED;
        };
    }

    @Override
    public void tick(long elapsedSeconds) {
        this.elapsedSeconds = elapsedSeconds;
    }

    @Override
    public void cancel() {
        this.cancelled = true;
        this.status = SkyEventStatus.CANCELLED;
        this.phase = SkyEventPhase.CANCELLED;
    }

    protected EventState toState() {
        return new EventState(
            id, type, serverId, scope, scheduledAt, startedAt, endsAt,
            status, phase, elapsedSeconds, getExtraData()
        );
    }

    protected Map<String, Object> getExtraData() {
        return Map.of();
    }
}