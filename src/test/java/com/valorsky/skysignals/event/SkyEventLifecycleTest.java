package com.valorsky.skysignals.event;

import com.valorsky.skysignals.model.SkyEventStatus;
import com.valorsky.skysignals.model.SkyEventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SkyEventLifecycleTest {

    @Test
    void testStatusTransitions() {
        TestEvent event = new TestEvent();
        assertEquals(SkyEventStatus.SCHEDULED, event.getStatus());

        event.start();
        assertEquals(SkyEventStatus.ACTIVE, event.getStatus());

        event.stop();
        assertEquals(SkyEventStatus.FINISHED, event.getStatus());
    }

    @Test
    void testCancelTransitionsToCancelled() {
        TestEvent event = new TestEvent();
        event.start();
        event.cancel();
        assertEquals(SkyEventStatus.CANCELLED, event.getStatus());
    }

    @Test
    void testExpiredCheckWhenActive() {
        TestEvent event = new TestEvent();
        event.start();
        assertTrue(event.isExpired());
    }

    @Test
    void testNotExpiredWhenScheduled() {
        TestEvent event = new TestEvent();
        assertFalse(event.isExpired());
    }

    @Test
    void testProgressDecreasesOverTime() {
        TestEvent event = new TestEvent(Instant.now().minusSeconds(300), Instant.now().plusSeconds(300));
        event.start();
        double initialProgress = event.getProgress();
        try {
            Thread.sleep(1100);
        } catch (InterruptedException ignored) {}
        double laterProgress = event.getProgress();
        assertTrue(laterProgress < initialProgress);
    }

    @Test
    void testSecondsRemaining() {
        TestEvent event = new TestEvent();
        event.start();
        assertEquals(0, event.getSecondsRemaining());
    }

    private static class TestEvent implements com.valorsky.skysignals.model.SkyEvent {
        private SkyEventStatus status = SkyEventStatus.SCHEDULED;
        private final Instant startedAt;
        private final Instant endsAt;

        TestEvent() {
            this.startedAt = Instant.now();
            this.endsAt = Instant.now().minusSeconds(1);
        }

        TestEvent(Instant startedAt, Instant endsAt) {
            this.startedAt = startedAt;
            this.endsAt = endsAt;
        }

        @Override public java.util.UUID getId() { return java.util.UUID.randomUUID(); }
        @Override public SkyEventType getType() { return SkyEventType.METEOR; }
        @Override public String getServerId() { return "test"; }
        @Override public Instant getStartedAt() { return startedAt; }
        @Override public Instant getEndsAt() { return endsAt; }
        @Override public SkyEventStatus getStatus() { return status; }
        @Override public void start() { status = SkyEventStatus.ACTIVE; }
        @Override public void tick() {}
        @Override public void stop() { status = SkyEventStatus.FINISHED; }
        @Override public void cancel() { status = SkyEventStatus.CANCELLED; }
    }
}
