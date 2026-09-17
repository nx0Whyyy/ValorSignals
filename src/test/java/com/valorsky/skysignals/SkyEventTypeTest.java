package com.valorsky.skysignals;

import com.valorsky.skysignals.model.SkyEventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkyEventTypeTest {

    @Test
    void testFromId() {
        assertEquals(SkyEventType.METEOR, SkyEventType.fromId("METEOR"));
        assertEquals(SkyEventType.METEOR, SkyEventType.fromId("meteor"));
        assertEquals(SkyEventType.METEOR, SkyEventType.fromId("MeTeOr"));
        assertEquals(SkyEventType.SKY_CHEST, SkyEventType.fromId("SKY_CHEST"));
        assertEquals(SkyEventType.SKY_CHEST, SkyEventType.fromId("sky-chest"));
        assertNull(SkyEventType.fromId("NONEXISTENT"));
        assertNull(SkyEventType.fromId(null));
    }

    @Test
    void testAllTypesHaveSymbolAndDisplayName() {
        for (SkyEventType type : SkyEventType.values()) {
            assertNotNull(type.symbol());
            assertFalse(type.symbol().isEmpty());
            assertNotNull(type.displayName());
            assertFalse(type.displayName().isEmpty());
        }
    }

    @Test
    void testEventTypeCount() {
        assertEquals(6, SkyEventType.values().length);
    }
}
