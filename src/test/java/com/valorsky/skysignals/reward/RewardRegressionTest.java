package com.valorsky.skysignals.reward;
import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.database.DatabaseManager;
import com.valorsky.skysignals.model.SkyEventType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RewardRegressionTest {
    @Test void testsCannotGiveMoneyItemsOrCommandsByDefault() {
        var plugin = mock(JavaPlugin.class); when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        var database = mock(DatabaseManager.class);
        var service = new RewardService(plugin, mock(Config.class), database);
        var eventId = UUID.randomUUID(); service.markTestEvent(eventId);
        assertFalse(service.rewardsAllowed(eventId));
        var result = service.giveRewards(eventId, SkyEventType.SKY_CHEST, UUID.randomUUID(), "test").join();
        assertFalse(result.success());
        verifyNoInteractions(database);
    }
}
