package com.valorsky.skysignals.notification;

import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.config.MessageConfig;
import com.valorsky.skysignals.model.NotificationLevel;
import com.valorsky.skysignals.model.SkyEvent;
import com.valorsky.skysignals.model.SkyEventType;
import com.valorsky.skysignals.sound.SoundService;
import com.valorsky.skysignals.util.FoliaScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NotificationService {

    private final JavaPlugin plugin;
    private final Config config;
    private final MessageConfig messages;
    private final MiniMessage miniMessage;
    private final TitleService titleService;
    private final ActionBarService actionBarService;
    private final BossBarService bossBarService;
    private final SoundService soundService;
    private final Map<UUID, String> activeBossBarIds = new ConcurrentHashMap<>();
    private final Map<UUID, FoliaScheduler.TaskHandle> bossBarTasks = new ConcurrentHashMap<>();

    public NotificationService(
            JavaPlugin plugin,
            Config config,
            MessageConfig messages,
            TitleService titleService,
            ActionBarService actionBarService,
            BossBarService bossBarService,
            SoundService soundService
    ) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.miniMessage = MiniMessage.miniMessage();
        this.titleService = titleService;
        this.actionBarService = actionBarService;
        this.bossBarService = bossBarService;
        this.soundService = soundService;
    }

    public void notifyEventStart(SkyEvent event) {
        notifyEvent(event, "start", NotificationLevel.GLOBAL);
        createBossBar(event);
    }

    public void notifyEventPhase(SkyEvent event, String messageKey) {
        notifyEvent(event, messageKey, NotificationLevel.GLOBAL);
    }

    public void notifyEventEnd(SkyEvent event) {
        notifyEvent(event, "finish", NotificationLevel.GLOBAL);
        removeBossBar(event.getId());
    }

    public void notifyEvent(SkyEvent event, String messageKey, NotificationLevel level) {
        SkyEventType type = event.getType();
        String path = type.configKey() + "." + messageKey;
        String template = messages.get(path);
        if (template.isEmpty()) {
            template = messages.get(type.configKey() + ".start");
        }
        if (template.isEmpty()) {
            template = "<yellow>" + type.symbol() + " " + type.displayName();
        }

        Component component = miniMessage.deserialize(template,
            Placeholder.parsed("direction", "Nord"),
            Placeholder.parsed("event", type.displayName()),
            Placeholder.parsed("server", event.getServerId()),
            Placeholder.parsed("time", formatTime(event.getSecondsRemaining())),
            Placeholder.parsed("wave", "1"),
            Placeholder.parsed("max", "1"),
            Placeholder.parsed("count", "0")
        );

        List<Player> audience = getAudience(event, level);

        if (config.notificationsChat()) {
            for (Player player : audience) {
                player.sendMessage(component);
            }
        }

        if (config.actionBarEnabled()) {
            Component actionBarMsg = miniMessage.deserialize(
                type.symbol() + " " + type.displayName() + " <gray>" + formatTime(event.getSecondsRemaining())
            );
            actionBarService.send(actionBarMsg, audience);
        }

        if (config.soundsEnabled()) {
            soundService.playGlobal("global_announce");
        }
    }

    private List<Player> getAudience(SkyEvent event, NotificationLevel level) {
        List<Player> result = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            result.add(player);
        }
        return result;
    }

    private void createBossBar(SkyEvent event) {
        if (!config.bossBarEnabled()) return;

        String path = event.getType().configKey() + ".bossbar";
        String template = messages.get(path, event.getType().symbol() + " " + event.getType().displayName() + " - %time%");
        String formatted = template.replace("%time%", formatTime(event.getSecondsRemaining()));
        Component title = miniMessage.deserialize(formatted);

        String bossBarId = UUID.randomUUID().toString();
        activeBossBarIds.put(event.getId(), bossBarId);

        List<Player> audience = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            audience.add(player);
        }
        bossBarService.create(bossBarId, title, 1.0f, audience);

        FoliaScheduler.TaskHandle taskHandle = FoliaScheduler.runGlobalTimer(plugin, () -> {
            String barId = activeBossBarIds.get(event.getId());
            if (barId != null) {
                String formattedNow = template.replace("%time%", formatTime(event.getSecondsRemaining()));
                bossBarService.update(barId, miniMessage.deserialize(formattedNow), (float) event.getProgress());
            }
        }, 20L, 20L);
        bossBarTasks.put(event.getId(), taskHandle);
    }

    private void removeBossBar(UUID eventId) {
        String bossBarId = activeBossBarIds.remove(eventId);
        FoliaScheduler.TaskHandle taskHandle = bossBarTasks.remove(eventId);
        if (taskHandle != null) {
            taskHandle.cancel();
        }
        if (bossBarId != null) {
            bossBarService.remove(bossBarId);
        }
    }

    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    public void cleanup() {
        for (FoliaScheduler.TaskHandle handle : bossBarTasks.values()) {
            handle.cancel();
        }
        bossBarTasks.clear();
        for (String bossBarId : activeBossBarIds.values()) {
            bossBarService.remove(bossBarId);
        }
        activeBossBarIds.clear();
    }
}