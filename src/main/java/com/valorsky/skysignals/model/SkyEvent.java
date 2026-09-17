package com.valorsky.skysignals.model;

import java.time.Instant;
import java.util.UUID;

public interface SkyEvent {

    UUID getId();

    SkyEventType getType();

    String getServerId();

    EventScope getScope();

    Instant getScheduledAt();

    Instant getStartedAt();

    Instant getEndsAt();

    SkyEventStatus getStatus();

    SkyEventPhase getPhase();

    long getElapsedSeconds();

    default boolean isExpired() {
        return Instant.now().isAfter(getEndsAt()) &&
               (getStatus() == SkyEventStatus.ACTIVE ||
                getStatus() == SkyEventStatus.ANNOUNCING ||
                getStatus() == SkyEventStatus.WARNING);
    }

    void start();

    void onPhaseChange(SkyEventPhase phase);

    void tick(long elapsedSeconds);

    void stop();

    void cancel();

    default double getProgress() {
        Instant now = Instant.now();
        long total = getEndsAt().getEpochSecond() - getStartedAt().getEpochSecond();
        long elapsed = now.getEpochSecond() - getStartedAt().getEpochSecond();
        if (total <= 0) return 0.0;
        return Math.min(1.0, Math.max(0.0, (double) elapsed / (double) total));
    }

    default long getSecondsRemaining() {
        return Math.max(0, getEndsAt().getEpochSecond() - Instant.now().getEpochSecond());
    }
}