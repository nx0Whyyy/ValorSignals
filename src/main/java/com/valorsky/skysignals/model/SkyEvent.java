package com.valorsky.skysignals.model;

import java.time.Instant;
import java.util.UUID;

public interface SkyEvent {

    UUID getId();

    SkyEventType getType();

    String getServerId();

    Instant getStartedAt();

    Instant getEndsAt();

    SkyEventStatus getStatus();

    default boolean isExpired() {
        return Instant.now().isAfter(getEndsAt()) && getStatus() == SkyEventStatus.ACTIVE;
    }

    void start();

    void tick();

    void stop();

    void cancel();

    default double getProgress() {
        Instant now = Instant.now();
        long total = getEndsAt().getEpochSecond() - getStartedAt().getEpochSecond();
        long remaining = getEndsAt().getEpochSecond() - now.getEpochSecond();
        if (total <= 0) return 0.0;
        return Math.max(0.0, (double) remaining / (double) total);
    }

    default long getSecondsRemaining() {
        return Math.max(0, getEndsAt().getEpochSecond() - Instant.now().getEpochSecond());
    }
}
