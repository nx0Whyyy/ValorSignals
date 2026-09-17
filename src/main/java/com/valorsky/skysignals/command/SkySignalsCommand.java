package com.valorsky.skysignals.command;

import com.valorsky.skysignals.api.SkySignalsAPI;
import com.valorsky.skysignals.cache.CacheService;
import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.config.MessageConfig;
import com.valorsky.skysignals.event.SkyEventManager;
import com.valorsky.skysignals.event.impl.*;
import com.valorsky.skysignals.event.EventContext;
import com.valorsky.skysignals.model.SkyEvent;
import com.valorsky.skysignals.model.SkyEventType;
import com.valorsky.skysignals.notification.NotificationService;
import com.valorsky.skysignals.redis.RedisService;
import com.valorsky.skysignals.rabbitmq.RabbitManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public final class SkySignalsCommand implements TabExecutor {

    private final JavaPlugin plugin;
    private final SkySignalsAPI api;
    private final Config config;
    private final MessageConfig messages;
    private final CacheService cacheService;
    private final RedisService redisService;
    private final RabbitManager rabbitManager;
    private final Logger logger;
    private final MiniMessage miniMessage;
    private final NotificationService notificationService;
    private final EventContext eventContext;

    public SkySignalsCommand(
            JavaPlugin plugin,
            SkySignalsAPI api,
            Config config,
            MessageConfig messages,
            CacheService cacheService,
            RedisService redisService,
            RabbitManager rabbitManager,
            NotificationService notificationService,
            EventContext eventContext
    ) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
        this.messages = messages;
        this.cacheService = cacheService;
        this.redisService = redisService;
        this.rabbitManager = rabbitManager;
        this.notificationService = notificationService;
        this.eventContext = eventContext;
        this.logger = plugin.getLogger();
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "info" -> cmdInfo(sender);
            case "active" -> cmdActive(sender);
            case "history" -> cmdHistory(sender, args);
            case "reload" -> cmdReload(sender);
            case "start" -> cmdStart(sender, args);
            case "stop" -> cmdStop(sender, args);
            case "debug" -> cmdDebug(sender);
            case "test" -> cmdTest(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(miniMessage.deserialize(messages.prefix() + " <gray>Utilisation: /skysignals <subcommand>"));
        sender.sendMessage(miniMessage.deserialize("<gray>Sous-commandes: info, active, history, reload, start, stop, debug, test"));
    }

    private void cmdInfo(CommandSender sender) {
        sender.sendMessage(miniMessage.deserialize("<gradient:#9333ea:#3b82f6>SkySignals Info</gradient>"));
        sender.sendMessage(miniMessage.deserialize("<gray>-------------------------</gray>"));
        sender.sendMessage("");
        sender.sendMessage(miniMessage.deserialize("<gray>Serveur: <white>" + config.serverId() + "</gray>"));
        sender.sendMessage("");
        sender.sendMessage(miniMessage.deserialize("<gray>Événements actifs: <white>" + api.getActiveEvents().size() + "</gray>"));
        sender.sendMessage("");
        sender.sendMessage(miniMessage.deserialize("<gray>État des services:</gray>"));
        sender.sendMessage(miniMessage.deserialize("  <gray>Redis: <white>" + formatStatus(redisService != null && redisService.isConnected()) + "</white>"));
        sender.sendMessage(miniMessage.deserialize("  <gray>RabbitMQ: <white>" + formatStatus(rabbitManager != null && rabbitManager.isConnected()) + "</white>"));
        sender.sendMessage(miniMessage.deserialize("  <gray>Base de données: <white>" + formatStatus(api.getDatabaseConnected()) + "</white>"));
        sender.sendMessage(miniMessage.deserialize("  <gray>Cache: <white>" + cacheService.size() + " entrées</gray>"));
        sender.sendMessage("");
        sender.sendMessage(miniMessage.deserialize("<gray>Événements enregistrés: <white>" + api.getEventManager().getRegisteredEventTypes().size() + "</gray>"));
    }

    private String formatStatus(boolean connected) {
        return connected ? "<green>CONNECTÉ</green>" : "<red>DÉCONNECTÉ</red>";
    }

    private void cmdActive(CommandSender sender) {
        List<SkyEvent> active = api.getActiveEvents();
        if (active.isEmpty()) {
            sender.sendMessage(miniMessage.deserialize(messages.prefix() + " <gray>Aucun événement actif.</gray>"));
            return;
        }
        sender.sendMessage(miniMessage.deserialize("<yellow>Événements actifs:</yellow>"));
        for (SkyEvent event : active) {
            sender.sendMessage(miniMessage.deserialize(
                    "  <yellow>" + event.getType().symbol() + " " + event.getType().displayName() + "</yellow> " +
                    "<gray>Phase: " + event.getPhase() + " | Temps restant: " + formatTime(event.getSecondsRemaining()) + "</gray>"
            ));
        }
    }

    private void cmdHistory(CommandSender sender, String[] args) {
        sender.sendMessage(miniMessage.deserialize("<gradient:#9333ea:#3b82f6>☁ HISTORIQUE SKY SIGNALS</gradient>"));
        var events = api.getRecentEvents(10);
        if (events.isEmpty()) {
            sender.sendMessage(miniMessage.deserialize("<gray>Aucun événement dans l'historique.</gray>"));
            return;
        }
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
            }
        }
        for (var entry : events) {
            long minutes = Math.max(0, (Instant.now().toEpochMilli() - entry.endedAt().toEpochMilli()) / 60000);
            sender.sendMessage(miniMessage.deserialize(
                    "<gray>" + entry.type().symbol() + " <white>" + entry.type().displayName() + "</white>" +
                    " <dark_gray>- Il y a " + (minutes == 0 ? "moins d'une minute" : minutes + " minutes") + "</dark_gray>"
            ));
        }
    }

    private void cmdReload(CommandSender sender) {
        if (!sender.hasPermission("valorsky.skysignals.reload")) {
            sender.sendMessage(miniMessage.deserialize(messages.getWithPrefix("no-permission")));
            return;
        }
        api.reload();
        sender.sendMessage(miniMessage.deserialize(messages.getWithPrefix("reloaded")));
    }

    private void cmdStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("valorsky.skysignals.start")) {
            sender.sendMessage(miniMessage.deserialize(messages.getWithPrefix("no-permission")));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(miniMessage.deserialize("<gray>Usage: /skysignals start <event></gray>"));
            return;
        }
        SkyEventType type = SkyEventType.fromId(args[1]);
        if (type == null) {
            sender.sendMessage(miniMessage.deserialize("<red>Événement inconnu.</red>"));
            return;
        }
        if (!config.isEventEnabled(type)) {
            sender.sendMessage(miniMessage.deserialize("<red>Cet événement est désactivé.</red>"));
            return;
        }
        api.getEventManager().startEvent(type, eventContext);
        sender.sendMessage(miniMessage.deserialize(messages.getWithPrefix("event-started").replace("%event%", type.displayName())));
    }

    private void cmdStop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("valorsky.skysignals.stop")) {
            sender.sendMessage(miniMessage.deserialize(messages.getWithPrefix("no-permission")));
            return;
        }
        List<SkyEvent> active = api.getActiveEvents();
        if (active.isEmpty()) {
            sender.sendMessage(miniMessage.deserialize("<gray>Aucun événement actif.</gray>"));
            return;
        }
        if (args.length >= 2) {
            try {
                UUID eventId = UUID.fromString(args[1]);
                api.getEventManager().cancelEvent(eventId);
                sender.sendMessage(miniMessage.deserialize("<green>Événement arrêté.</green>"));
            } catch (IllegalArgumentException e) {
                sender.sendMessage(miniMessage.deserialize("<red>ID d'événement invalide.</red>"));
            }
        } else {
            for (SkyEvent event : active) {
                api.getEventManager().cancelEvent(event.getId());
            }
            sender.sendMessage(miniMessage.deserialize(messages.getWithPrefix("event-stopped")));
        }
    }

    private void cmdDebug(CommandSender sender) {
        if (!sender.hasPermission("valorsky.skysignals.debug")) {
            sender.sendMessage(miniMessage.deserialize(messages.getWithPrefix("no-permission")));
            return;
        }
        sender.sendMessage(miniMessage.deserialize("<gradient:#9333ea:#3b82f6>SkySignals Debug</gradient>"));
        sender.sendMessage(miniMessage.deserialize("<gray>-----------------------</gray>"));
        sender.sendMessage("");
        sender.sendMessage(miniMessage.deserialize("<gray>Serveur: <white>" + config.serverId() + "</gray>"));
        sender.sendMessage("");
        sender.sendMessage(miniMessage.deserialize("<gray>Événements:</gray>"));
        sender.sendMessage(miniMessage.deserialize("  <gray>Actifs: <white>" + api.getActiveEvents().size() + "</white>"));
        sender.sendMessage("");
        sender.sendMessage(miniMessage.deserialize("<gray>Cache:</gray>"));
        sender.sendMessage(miniMessage.deserialize("  <gray>Taille: <white>" + cacheService.size() + "</white>"));
        sender.sendMessage(miniMessage.deserialize("  <gray>Taux de cache: <white>" + (int) (cacheService.hitRate() * 100) + "%</white>"));
        sender.sendMessage("");
        sender.sendMessage(miniMessage.deserialize("<gray>Redis:</gray>"));
        sender.sendMessage(miniMessage.deserialize("  <white>" + formatStatus(redisService != null && redisService.isConnected()) + "</white>"));
        sender.sendMessage("");
        sender.sendMessage(miniMessage.deserialize("<gray>RabbitMQ:</gray>"));
        sender.sendMessage(miniMessage.deserialize("  <white>" + formatStatus(rabbitManager != null && rabbitManager.isConnected()) + "</white>"));
        sender.sendMessage("");
        sender.sendMessage(miniMessage.deserialize("<gray>Base de données:</gray>"));
        sender.sendMessage(miniMessage.deserialize("  <white>" + formatStatus(api.getDatabaseConnected()) + "</white>"));
        sender.sendMessage("");
        sender.sendMessage(miniMessage.deserialize("<gray>Événements enregistrés:</gray>"));
        for (var type : api.getEventManager().getRegisteredEventTypes()) {
            sender.sendMessage(miniMessage.deserialize("  <white>" + type.symbol() + " " + type.displayName() + "</white>"));
        }
    }

    private void cmdTest(CommandSender sender, String[] args) {
        if (!sender.hasPermission("valorsky.skysignals.test")) {
            sender.sendMessage(miniMessage.deserialize(messages.getWithPrefix("no-permission")));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(miniMessage.deserialize("<gray>Usage: /skysignals test <event></gray>"));
            return;
        }
        SkyEventType type = SkyEventType.fromId(args[1]);
        if (type == null) {
            sender.sendMessage(miniMessage.deserialize("<red>Événement inconnu.</red>"));
            return;
        }
        if (!config.testingVisuals()) {
            sender.sendMessage(miniMessage.deserialize("<yellow>Les tests visuels sont désactivés.</yellow>"));
        }
        api.getEventManager().startEvent(type, eventContext);
        sender.sendMessage(miniMessage.deserialize("<green>Test de " + type.displayName() + " lancé (sans récompenses).</green>"));
    }

    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = Arrays.asList("info", "active", "history", "reload", "start", "stop", "debug", "test");
            for (String sub : subs) {
                if (sender.hasPermission("valorsky.skysignals." + sub) || sub.equals("info") || sub.equals("active") || sub.equals("history")) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("test")) {
                for (SkyEventType type : SkyEventType.values()) {
                    completions.add(type.name().toLowerCase().replace("_", "-"));
                }
            } else if (args[0].equalsIgnoreCase("stop")) {
                for (SkyEvent event : api.getActiveEvents()) {
                    completions.add(event.getId().toString());
                }
            }
        }

        return completions;
    }
}