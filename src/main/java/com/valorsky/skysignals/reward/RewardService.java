package com.valorsky.skysignals.reward;

import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.database.DatabaseManager;
import com.valorsky.skysignals.model.SkyEventType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.valorsky.skysignals.util.FoliaScheduler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public final class RewardService {

    private final JavaPlugin plugin;
    private final Config config;
    private final DatabaseManager databaseManager;
    private final Logger logger;

    public RewardService(JavaPlugin plugin, Config config, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.config = config;
        this.databaseManager = databaseManager;
        this.logger = plugin.getLogger();
    }

    public CompletableFuture<RewardResult> giveRewards(UUID eventId, SkyEventType eventType, UUID playerId, String serverId) {
        return CompletableFuture.supplyAsync(() -> {
            String claimKey = eventId + ":" + playerId + ":" + eventType.name();

            if (hasClaimed(claimKey)) {
                return new RewardResult(false, "already_claimed", List.of());
            }

            try {
                RewardContext context = new RewardContext(
                    eventId, eventType, playerId, serverId,
                    System.currentTimeMillis(), buildPlaceholders(eventId, eventType, playerId, serverId)
                );

                List<RewardType> rewards = buildRewards(eventType, context);
                Player player = Bukkit.getPlayer(playerId);

                if (player != null && player.isOnline()) {
                    FoliaScheduler.runEntity(plugin, player, () -> {
                        giveMoney(player, context);
                        runCommands(player, context);
                        giveItems(player, rewards);
                    });
                }

                recordClaim(claimKey);
                return new RewardResult(true, "success", rewards);

            } catch (Exception e) {
                logger.severe("Failed to give rewards: " + e.getMessage());
                return new RewardResult(false, "error: " + e.getMessage(), List.of());
            }
        }, databaseManager.asyncExecutor());
    }

    private boolean hasClaimed(String claimKey) {
        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT 1 FROM sky_signals_rewards WHERE claim_key = ?"
             )) {
            stmt.setString(1, claimKey);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            logger.warning("Failed to check reward claim: " + e.getMessage());
            return false;
        }
    }

    private void recordClaim(String claimKey) {
        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT IGNORE INTO sky_signals_rewards (claim_key, claimed_at) VALUES (?, NOW())"
             )) {
            stmt.setString(1, claimKey);
            stmt.executeUpdate();
        } catch (Exception e) {
            logger.warning("Failed to record reward claim: " + e.getMessage());
        }
    }

    private Map<String, String> buildPlaceholders(UUID eventId, SkyEventType eventType, UUID playerId, String serverId) {
        Player player = Bukkit.getPlayer(playerId);
        return Map.of(
            "player", player != null ? player.getName() : playerId.toString(),
            "uuid", playerId.toString(),
            "event", eventType.name(),
            "event_id", eventId.toString(),
            "server", serverId
        );
    }

    private List<RewardType> buildRewards(SkyEventType eventType, RewardContext context) {
        List<RewardType> rewards = new ArrayList<>();

        int moneyMin = config.getRewardMoneyMin(eventType);
        int moneyMax = config.getRewardMoneyMax(eventType);
        if (moneyMax > 0) {
            double amount = moneyMin + Math.random() * (moneyMax - moneyMin);
            rewards.add(new MoneyReward(amount));
        }

        for (String cmd : config.getRewardCommands(eventType)) {
            String processed = cmd
                .replace("%player%", context.placeholders().get("player"))
                .replace("%uuid%", context.placeholders().get("uuid"))
                .replace("%event%", context.placeholders().get("event"))
                .replace("%event_id%", context.placeholders().get("event_id"))
                .replace("%server%", context.placeholders().get("server"));
            rewards.add(new CommandReward(processed));
        }

        String path = "rewards." + eventType.configKey() + ".items";
        List<?> itemsConfig = config.getConfig().getList(path);
        if (itemsConfig != null) {
            for (Object obj : itemsConfig) {
                if (obj instanceof Map<?, ?> map) {
                    String materialStr = (String) map.get("material");
                    Material material = Material.matchMaterial(materialStr);
                    if (material != null) {
                        int amount = map.get("amount") instanceof Number n ? n.intValue() : 1;
                        String name = map.get("name") instanceof String s ? s : "";
                        rewards.add(new ItemReward(material, amount, name.isEmpty() ? null : Component.text(name), List.of()));
                    }
                }
            }
        }

        return rewards;
    }

    private void giveMoney(Player player, RewardContext context) {
        for (RewardType reward : buildRewards(context.eventType(), context)) {
            if (reward instanceof MoneyReward mr) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "eco give " + player.getName() + " " + (long) mr.amount());
            }
        }
    }

    private void runCommands(Player player, RewardContext context) {
        for (RewardType reward : buildRewards(context.eventType(), context)) {
            if (reward instanceof CommandReward cr) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cr.command());
            }
        }
    }

    private void giveItems(Player player, List<RewardType> rewards) {
        for (RewardType reward : rewards) {
            if (reward instanceof ItemReward ir) {
                ItemStack stack = new ItemStack(ir.material(), Math.max(1, ir.amount()));
                if (ir.name() != null) {
                    var meta = stack.getItemMeta();
                    if (meta != null) {
                        meta.displayName(ir.name());
                        stack.setItemMeta(meta);
                    }
                }
                player.getInventory().addItem(stack);
            }
        }
    }
}