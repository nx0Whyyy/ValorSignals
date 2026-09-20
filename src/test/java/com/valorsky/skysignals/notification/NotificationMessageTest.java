package com.valorsky.skysignals.notification;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NotificationMessageTest {
    @Test void replacesLegacyAndMiniMessageTimePlaceholders() {
        var component = NotificationService.renderMessage("<gray>Restant : <yellow>%time%</yellow> / <time></gray>", "2 min 49 s");
        assertEquals("Restant : 2 min 49 s / 2 min 49 s", PlainTextComponentSerializer.plainText().serialize(component));
    }
    @Test void formatsReadableDurations() {
        assertEquals("2 min 49 s", NotificationService.formatChatTime(169));
        assertEquals("1 min", NotificationService.formatChatTime(60));
        assertEquals("10 s", NotificationService.formatChatTime(10));
        assertEquals("0 s", NotificationService.formatChatTime(-1));
    }
}
