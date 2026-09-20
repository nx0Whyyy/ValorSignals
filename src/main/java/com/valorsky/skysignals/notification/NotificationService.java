package com.valorsky.skysignals.notification;

import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.config.MessageConfig;
import com.valorsky.skysignals.model.NotificationLevel;
import com.valorsky.skysignals.model.EventLocation;
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
    private final Map<UUID, List<EventLocation>> announcedLocations = new ConcurrentHashMap<>();
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
        announcedLocations.remove(event.getId());
    }

    public void notifyEvent(SkyEvent event, String messageKey, NotificationLevel level) {
        SkyEventType type = event.getType();
        String path = type.configKey() + "." + messageKey;
        String template = messages.get(path);
        if (template.isEmpty() && !messageKey.equals("start") && !messageKey.equals("finish")) return;
        if (template.isEmpty()) {
            template = "<yellow>" + type.symbol() + " " + type.displayName();
        }

        // Keep existing messages.yml files compatible with the original bundled text.
        if (type == SkyEventType.METEOR && messageKey.equals("warning") && template.equals(
                "<red>☄ IMPACT IMMINENT\n<white>Impact dans <yellow>%time%</white> secondes")) {
            template = "<red>☄ Impact imminent !</red>\n<gray>Temps restant de l’événement : <yellow>%time%</yellow></gray>";
        }
        if (type == SkyEventType.SKY_CHEST && messageKey.equals("unlocking") && template.equals(
                "<yellow>🔒 CAISSE CÉLESTE\n<white>Déverrouillage dans <yellow>%time%</white>s")) {
            template = "<yellow>🔒 Caisse céleste</yellow>\n<gray>Déverrouillage dans <yellow>%time%</yellow></gray>";
        }
        long seconds = type == SkyEventType.SKY_CHEST && messageKey.equals("unlocking")
                ? 10 : event.getSecondsRemaining();
        Component component = renderMessage(template, formatChatTime(seconds),

            Placeholder.unparsed("direction", "Nord"),
            Placeholder.unparsed("event", type.displayName()),
            Placeholder.unparsed("server", event.getServerId()),
            Placeholder.unparsed("wave", "1"),
            Placeholder.unparsed("max", "1"),
            Placeholder.unparsed("count", "0")
        );

        List<Player> audience = getAudience(event, level);
        List<EventLocation> locations = event.getEventLocations();
        boolean arrival = messageKey.equals("landing") || messageKey.equals("impact");
        if (config.notificationsChat() && !audience.isEmpty() && !locations.isEmpty()
                && !messageKey.equals("finish")
                && (arrival || !locations.equals(announcedLocations.put(event.getId(), List.copyOf(locations))))) {
            component = component.append(Component.newline()).append(formatLocations(locations));
        }

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

    public Component describeLocation(SkyEvent event) {
        List<EventLocation> locations = event.getEventLocations();
        if (locations.isEmpty()) {
            return miniMessage.deserialize(messages.get("location.pending", "<gray>📍 Localisation en cours de recherche…</gray>"));
        }
        return formatLocations(locations);
    }

    private Component formatLocations(List<EventLocation> locations) {
        Component result = Component.empty();
        for (int i = 0; i < locations.size(); i++) {
            if (i > 0) result = result.append(Component.newline());
            result = result.append(formatLocation(locations.get(i)));
        }
        return result;
    }

    private Component formatLocation(EventLocation location) {
        if (location.position() == null) {
            return renderMessage(messages.get("location.world", "<gray>📍 %label% <dark_gray>•</dark_gray> Monde : <aqua>%world%</aqua></gray>"), "",
                    Placeholder.unparsed("label", location.label()), Placeholder.unparsed("world", location.world()));
        }
        String fallback = "<gray>📍 %label% <dark_gray>•</dark_gray> Monde : <aqua>%world%</aqua>"
                + "\nX : <yellow>%x%</yellow> <dark_gray>•</dark_gray> Y : <yellow>%y%</yellow> <dark_gray>•</dark_gray> Z : <yellow>%z%</yellow>"
                + (location.radius() > 0 ? " <dark_gray>•</dark_gray> Rayon : <yellow>%radius% blocs</yellow>" : "") + "</gray>";
        return renderMessage(messages.get(location.radius() > 0 ? "location.area" : "location.point", fallback), "",
                Placeholder.unparsed("label", location.label()), Placeholder.unparsed("world", location.world()),
                Placeholder.unparsed("x", Integer.toString(location.position().x())),
                Placeholder.unparsed("y", Integer.toString(location.position().y())),
                Placeholder.unparsed("z", Integer.toString(location.position().z())),
                Placeholder.unparsed("radius", Integer.toString(location.radius())));
    }

    static Component renderMessage(String template, String time,
                                   net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        for (String key : List.of("time", "direction", "event", "server", "wave", "max", "count", "x", "y", "z", "world", "label", "radius")) {
            template = template.replace("%" + key + "%", "<" + key + ">");
        }
        return MiniMessage.miniMessage().deserialize(template,
                net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver(resolvers),
                Placeholder.unparsed("time", time));
    }

    static String formatChatTime(long seconds) {
        seconds = Math.max(0, seconds);
        long minutes = seconds / 60;
        long remainder = seconds % 60;
        if (minutes == 0) return remainder + " s";
        return minutes + " min" + (remainder == 0 ? "" : " " + remainder + " s");
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
        announcedLocations.clear();
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