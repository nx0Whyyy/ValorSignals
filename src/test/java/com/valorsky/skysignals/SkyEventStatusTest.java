package com.valorsky.skysignals;

import com.valorsky.skysignals.model.SkyEventStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkyEventStatusTest {

    @Test
    void testStatusValues() {
        assertEquals(7, SkyEventStatus.values().length);
        assertTrue(java.util.Arrays.asList(SkyEventStatus.values()).contains(SkyEventStatus.SCHEDULED));
        assertTrue(java.util.Arrays.asList(SkyEventStatus.values()).contains(SkyEventStatus.ACTIVE));
        assertTrue(java.util.Arrays.asList(SkyEventStatus.values()).contains(SkyEventStatus.FINISHED));
        assertTrue(java.util.Arrays.asList(SkyEventStatus.values()).contains(SkyEventStatus.CANCELLED));
    }

    @Test
    void testValueOf() {
        assertEquals(SkyEventStatus.SCHEDULED, SkyEventStatus.valueOf("SCHEDULED"));
        assertEquals(SkyEventStatus.ACTIVE, SkyEventStatus.valueOf("ACTIVE"));
        assertEquals(SkyEventStatus.FINISHED, SkyEventStatus.valueOf("FINISHED"));
        assertEquals(SkyEventStatus.CANCELLED, SkyEventStatus.valueOf("CANCELLED"));
    }
}
