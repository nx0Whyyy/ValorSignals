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
    private final com.github.benmanes.caffeine.cache.Cache<String, Boolean> claims =
        com.github.benmanes.caffeine.cache.Caffeine.newBuilder().maximumSize(100000)
            .expireAfterWrite(java.time.Duration.ofDays(1)).build();
    private final com.github.benmanes.caffeine.cache.Cache<UUID, Boolean> testEvents =
        com.github.benmanes.caffeine.cache.Caffeine.newBuilder().maximumSize(10000).expireAfterWrite(java.time.Duration.ofDays(1)).build();

    public boolean rewardsAllowed(UUID eventId) { return testEvents.getIfPresent(eventId) == null || config.testingRewards(); }

    public void markTestEvent(UUID eventId) { testEvents.put(eventId, true); }
    public void forgetTestEvent(UUID eventId) { testEvents.invalidate(eventId); }


    public RewardService(JavaPlugin plugin, Config config, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.config = config;
        this.databaseManager = databaseManager;
        this.logger = plugin.getLogger();
    }

    public CompletableFuture<RewardResult> giveRewards(UUID eventId, SkyEventType eventType, UUID playerId, String serverId) {
        if (!rewardsAllowed(eventId)) {
            return CompletableFuture.completedFuture(new RewardResult(false, "test_rewards_disabled", List.of()));
        }
        String key = eventId + ":" + playerId + ":" + eventType.name();
        if (claims.asMap().putIfAbsent(key, true) != null) {
            return CompletableFuture.completedFuture(new RewardResult(false, "already_claimed", List.of()));
        }
        return CompletableFuture.supplyAsync(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) throw new IllegalStateException("player_offline");
            RewardContext context = new RewardContext(eventId, eventType, playerId, serverId,
                    System.currentTimeMillis(), buildPlaceholders(eventId, eventType, playerId, serverId));
            List<RewardType> rewards = buildRewards(eventType, context);
            if (databaseManager.isConnected() && !reserveClaim(key, context)) {
                return new PreparedReward(null, List.of());
            }
            return new PreparedReward(player, rewards);
        }, databaseManager.asyncExecutor()).thenCompose(prepared -> {
            if (prepared.player() == null) return CompletableFuture.completedFuture(
                    new RewardResult(false, "already_claimed", List.of()));
            CompletableFuture<Boolean> delivery = new CompletableFuture<>();
            Runnable retired = () -> delivery.complete(false);
            var task = prepared.player().getScheduler().run(plugin, t -> {
                if (!prepared.player().isOnline()) { retired.run(); return; }
                try {
                    giveItems(prepared.player(), prepared.rewards());
                    delivery.complete(true);
                } catch (Exception e) { delivery.completeExceptionally(e); }
            }, retired);
            if (task == null) retired.run();
            return delivery.thenCompose(delivered -> {
                if (!delivered) return CompletableFuture.supplyAsync(() -> {
                    releaseClaim(key);
                    claims.invalidate(key);
                    return new RewardResult(false, "player_offline", List.of());
                }, databaseManager.asyncExecutor());
                return FoliaScheduler.supplyGlobal(plugin, () -> {
                    giveMoney(prepared.player(), prepared.rewards());
                    runCommands(prepared.player(), prepared.rewards());
                    return new RewardResult(true, "success", prepared.rewards());
                });
            });
        }).exceptionally(error -> {
            logger.warning("Reward delivery failed for " + key + ": " + error.getMessage());
            if (error.getCause() instanceof IllegalStateException && ("player_offline".equals(error.getCause().getMessage()) || "Cannot reserve reward claim".equals(error.getCause().getMessage()))) claims.invalidate(key);
            return new RewardResult(false, "delivery_failed", List.of());
        });
    }

    private record PreparedReward(Player player, List<RewardType> rewards) {}

    private boolean reserveClaim(String key, RewardContext context) {
        try (Connection connection = databaseManager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT IGNORE INTO sky_signals_rewards (claim_key, event_id, player_uuid, event_type, reward_type, reward_data) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, key);
            statement.setString(2, context.eventId().toString());
            statement.setString(3, context.playerId().toString());
            statement.setString(4, context.eventType().name());
            statement.setString(5, "BUNDLE");
            statement.setString(6, "{}");
            return statement.executeUpdate() == 1;
        } catch (java.sql.SQLException e) { throw new IllegalStateException("Cannot reserve reward claim", e); }
    }

    private void releaseClaim(String key) {
        if (!databaseManager.isConnected()) return;
        try (Connection connection = databaseManager.getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM sky_signals_rewards WHERE claim_key = ?")) {
            statement.setString(1, key);
            statement.executeUpdate();
        } catch (java.sql.SQLException e) { logger.warning("Cannot release offline claim: " + e.getMessage()); }
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

    private void giveMoney(Player player, List<RewardType> rewards) {
        for (RewardType reward : rewards) {
            if (reward instanceof MoneyReward mr) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "eco give " + player.getName() + " " + (long) mr.amount());
            }
        }
    }

    private void runCommands(Player player, List<RewardType> rewards) {
        for (RewardType reward : rewards) {
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
                player.getInventory().addItem(stack).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }
        }
    }
}