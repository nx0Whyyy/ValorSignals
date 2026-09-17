package com.valorsky.skysignals.notification;

import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.config.MessageConfig;
import com.valorsky.skysignals.model.SkyEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public final class NotificationService {

    private final JavaPlugin plugin;
    private final Config config;
    private final MessageConfig messages;
    private final Logger logger;
    private final MiniMessage miniMessage;
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, BukkitTask> bossBarTasks = new HashMap<>();

    public NotificationService(JavaPlugin plugin, Config config, MessageConfig messages) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.logger = plugin.getLogger();
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void notifyEventStart(SkyEvent event) {
        String path = event.getType().name().toLowerCase().replace("_", "-");
        String template = messages.get(path + ".start");
        if (template.isEmpty()) {
            template = "<yellow>" + event.getType().symbol() + " " + event.getType().displayName();
        }
        Component component = miniMessage.deserialize(template,
                Placeholder.parsed("direction", "Nord-Est"),
                Placeholder.parsed("event", event.getType().displayName()),
                Placeholder.parsed("server", event.getServerId())
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (config.notificationsChat()) {
                player.sendMessage(component);
            }
            if (config.notificationsActionbar()) {
                player.sendActionBar(Component.text(event.getType().symbol() + " " + event.getType().displayName()));
            }
            if (config.notificationsSound()) {
                player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
            }
            if (config.notificationsParticles()) {
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation(), 10);
            }
        }

        createBossBar(event);
    }

    public void notifyEventEnd(SkyEvent event) {
        String path = event.getType().name().toLowerCase().replace("_", "-");
        String template = messages.get(path + ".end");
        if (template.isEmpty()) {
            template = "<green>L'événement est terminé.";
        }
        Component component = miniMessage.deserialize(template,
                Placeholder.parsed("event", event.getType().displayName()),
                Placeholder.parsed("server", event.getServerId())
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (config.notificationsChat()) {
                player.sendMessage(component);
            }
        }

        removeBossBar(event.getId());
    }

    private void createBossBar(SkyEvent event) {
        if (!config.notificationsBossbar()) return;

        String path = event.getType().name().toLowerCase().replace("_", "-");
        String template = messages.get(path + ".bossbar", event.getType().symbol() + " " + event.getType().displayName() + " - %time%");
        String formatted = template.replace("%time%", formatTime(event.getSecondsRemaining()));
        Component title = miniMessage.deserialize(formatted);

        BossBar bar = Bukkit.createBossBar(PlainTextComponentSerializer.plainText().serialize(title), BarColor.YELLOW, BarStyle.SOLID);
        bar.setProgress(1.0);

        bossBars.put(event.getId(), bar);
        for (Player player : Bukkit.getOnlinePlayers()) {
            bar.addPlayer(player);
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            BossBar existing = bossBars.get(event.getId());
            if (existing != null) {
                updateBossBar(event, existing);
            }
        }, 20L, 20L);
        bossBarTasks.put(event.getId(), task);
    }

    private void updateBossBar(SkyEvent event, BossBar bar) {
        String path = event.getType().name().toLowerCase().replace("_", "-");
        String template = messages.get(path + ".bossbar", event.getType().symbol() + " " + event.getType().displayName() + " - %time%");
        String formatted = template.replace("%time%", formatTime(event.getSecondsRemaining()));
        bar.setTitle(PlainTextComponentSerializer.plainText().serialize(miniMessage.deserialize(formatted)));
        long total = event.getEndsAt().getEpochSecond() - event.getStartedAt().getEpochSecond();
        long remaining = event.getEndsAt().getEpochSecond() - java.time.Instant.now().getEpochSecond();
        bar.setProgress(total > 0 ? (double) Math.max(0, remaining) / total : 0.0);
    }

    private void removeBossBar(UUID eventId) {
        BukkitTask task = bossBarTasks.remove(eventId);
        if (task != null) {
            task.cancel();
        }
        BossBar bar = bossBars.remove(eventId);
        if (bar != null) {
            bar.setVisible(false);
            for (Player player : Bukkit.getOnlinePlayers()) {
                bar.removePlayer(player);
            }
        }
    }

    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    public void cleanup() {
        for (BukkitTask task : bossBarTasks.values()) {
            task.cancel();
        }
        bossBarTasks.clear();
        for (BossBar bar : bossBars.values()) {
            bar.setVisible(false);
        }
        bossBars.clear();
    }
}
