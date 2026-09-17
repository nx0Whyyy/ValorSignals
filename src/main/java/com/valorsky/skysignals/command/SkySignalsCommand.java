package com.valorsky.skysignals.command;

import com.valorsky.skysignals.api.SkySignalsAPI;
import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.event.SkyEventManager;
import com.valorsky.skysignals.model.SkyEvent;
import com.valorsky.skysignals.model.SkyEventType;
import com.valorsky.skysignals.notification.NotificationService;
import com.valorsky.skysignals.redis.RedisService;
import com.valorsky.skysignals.rabbitmq.RabbitManager;
import com.valorsky.skysignals.cache.CacheService;
import com.valorsky.skysignals.config.MessageConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public final class SkySignalsCommand implements TabExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final SkySignalsAPI api;
    private final Config config;
    private final MessageConfig messages;
    private final Logger logger;
    private final MiniMessage miniMessage;
    private final CacheService cacheService;
    private final RedisService redisService;
    private final RabbitManager rabbitManager;
    private final NotificationService notificationService;

    public SkySignalsCommand(
            JavaPlugin plugin,
            SkySignalsAPI api,
            Config config,
            MessageConfig messages,
            CacheService cacheService,
            RedisService redisService,
            RabbitManager rabbitManager,
            NotificationService notificationService
    ) {
        this.plugin = plugin;
        this.api = api;
        this.config = config;
        this.messages = messages;
        this.cacheService = cacheService;
        this.redisService = redisService;
        this.rabbitManager = rabbitManager;
        this.notificationService = notificationService;
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
            case "stop" -> cmdStop(sender);
            case "debug" -> cmdDebug(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        String prefix = messages.prefix();
        sender.sendMessage(miniMessage.deserialize(prefix + " <gray>/skysignals <info|active|history|reload|start|stop|debug>"));
    }

    private void cmdInfo(CommandSender sender) {
        String prefix = messages.prefix();
        sender.sendMessage(miniMessage.deserialize(prefix + " <gold>ValorSky SkySignals</gold>"));
        sender.sendMessage(miniMessage.deserialize("<gray>Version: <white>1.0.0</gray>"));
        sender.sendMessage("");
        sender.sendMessage(miniMessage.deserialize("<gray>Events actifs: <white>" + api.getActiveEvents().size() + "</gray>"));
        sender.sendMessage(miniMessage.deserialize("<gray>Redis: <white>" + (redisService != null && redisService.isConnected() ? "Connected" : "Disconnected") + "</gray>"));
        sender.sendMessage(miniMessage.deserialize("<gray>RabbitMQ: <white>" + (rabbitManager != null && rabbitManager.isConnected() ? "Connected" : "Disconnected") + "</gray>"));
        sender.sendMessage(miniMessage.deserialize("<gray>Database: <white>" + (api.getDatabaseConnected() ? "Connected" : "Disconnected") + "</gray>"));
        sender.sendMessage(miniMessage.deserialize("<gray>Cache: <white>" + cacheService.size() + " entries</gray>"));
    }

    private void cmdActive(CommandSender sender) {
        List<SkyEvent> active = api.getActiveEvents();
        String prefix = messages.prefix();
        if (active.isEmpty()) {
            sender.sendMessage(miniMessage.deserialize(prefix + " <gray>Aucun événement actif.</gray>"));
            return;
        }
        for (SkyEvent event : active) {
            sender.sendMessage(miniMessage.deserialize(
                    "<yellow>" + event.getType().symbol() + " " + event.getType().displayName() + "</yellow> " +
                    "<gray>- " + formatTime(event.getSecondsRemaining()) + "</gray>"
            ));
        }
    }

    private void cmdHistory(CommandSender sender, String[] args) {
        sender.sendMessage(miniMessage.deserialize("<gray>☁ SkySignals History</gray>"));
        var events = api.getRecentEvents(10);
        if (events.isEmpty()) {
            sender.sendMessage(miniMessage.deserialize("<gray>Aucun événement dans l'historique.</gray>"));
            return;
        }
        for (var entry : events) {
            long minutes = (Instant.now().toEpochMilli() - entry.endedAt().toEpochMilli()) / 60000;
            sender.sendMessage(miniMessage.deserialize(
                    "<yellow>" + entry.type().symbol() + " " + entry.type().displayName() + " </yellow>" +
                    "<gray>Il y a " + Math.max(0, minutes) + " minutes</gray>"
            ));
        }
    }

    private void cmdReload(CommandSender sender) {
        if (!sender.hasPermission("valorsky.skysignals.reload")) {
            sender.sendMessage(miniMessage.deserialize(messages.getWithPrefix("no-permission")));
            return;
        }
        plugin.reloadConfig();
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
        api.getEventManager().startEvent(type);
        sender.sendMessage(miniMessage.deserialize(messages.getWithPrefix("event-started").replace("%event%", type.displayName())));
    }

    private void cmdStop(CommandSender sender) {
        if (!sender.hasPermission("valorsky.skysignals.stop")) {
            sender.sendMessage(miniMessage.deserialize(messages.getWithPrefix("no-permission")));
            return;
        }
        List<SkyEvent> active = api.getActiveEvents();
        if (active.isEmpty()) {
            sender.sendMessage(miniMessage.deserialize("<gray>Aucun événement actif.</gray>"));
            return;
        }
        for (SkyEvent event : active) {
            api.getEventManager().cancelEvent(event.getId());
        }
        sender.sendMessage(miniMessage.deserialize(messages.getWithPrefix("event-stopped")));
    }

    private void cmdDebug(CommandSender sender) {
        if (!sender.hasPermission("valorsky.skysignals.debug")) {
            sender.sendMessage(miniMessage.deserialize(messages.getWithPrefix("no-permission")));
            return;
        }
        sender.sendMessage(miniMessage.deserialize("<gold>SkySignals Debug</gold>"));
        sender.sendMessage(miniMessage.deserialize("<gray>-------------------------</gray>"));
        sender.sendMessage("");
        sender.sendMessage(miniMessage.deserialize("<gray>Server ID: <white>" + config.serverId() + "</gray>"));
        sender.sendMessage("");
        sender.sendMessage(miniMessage.deserialize("<gray>Active Events: <white>" + api.getActiveEvents().size() + "</gray>"));
        sender.sendMessage("");
        sender.sendMessage(miniMessage.deserialize("<gray>Cache:</gray>"));
        sender.sendMessage(miniMessage.deserialize("  <gray>Size: <white>" + cacheService.size() + "</white>"));
        sender.sendMessage(miniMessage.deserialize("  <gray>Hit Rate: <white>" + (int) (cacheService.hitRate() * 100) + "%</white>"));
        sender.sendMessage("");
        sender.sendMessage(miniMessage.deserialize("<gray>Redis:</gray>"));
        sender.sendMessage(miniMessage.deserialize("  <gray>Connected: <white>" + (redisService != null && redisService.isConnected()) + "</white>"));
        sender.sendMessage("");
        sender.sendMessage(miniMessage.deserialize("<gray>RabbitMQ:</gray>"));
        sender.sendMessage(miniMessage.deserialize("  <gray>Connected: <white>" + (rabbitManager != null && rabbitManager.isConnected()) + "</white>"));
        sender.sendMessage("");
        sender.sendMessage(miniMessage.deserialize("<gray>Database:</gray>"));
        sender.sendMessage(miniMessage.deserialize("  <gray>Connected: <white>" + api.getDatabaseConnected() + "</white>"));
        sender.sendMessage("");
        sender.sendMessage(miniMessage.deserialize("<gray>Registered Events:</gray>"));
        for (var type : api.getEventManager().getRegisteredEventTypes()) {
            sender.sendMessage(miniMessage.deserialize("  <white>" + type.name() + "</white>"));
        }
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
            completions.addAll(Arrays.asList("info", "active", "history", "reload", "start", "stop", "debug"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            for (SkyEventType type : SkyEventType.values()) {
                completions.add(type.name().toLowerCase().replace("_", "-"));
            }
        }
        return completions;
    }
}
