package com.valorsky.skysignals.reward;

import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.model.SkyEventType;
import com.valorsky.skysignals.util.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.logging.Logger;

public final class RewardService {

    private final JavaPlugin plugin;
    private final Config config;
    private final Logger logger;

    private final Set<UUID> rewardedPlayers = java.util.Collections.newSetFromMap(new WeakHashMap<>());

    public RewardService(JavaPlugin plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();
    }

    public void giveRewards(Player player, SkyEventType eventType) {
        UUID playerId = player.getUniqueId();
        if (rewardedPlayers.contains(playerId)) {
            logger.warning("Player " + player.getName() + " already rewarded for " + eventType + ", skipping.");
            return;
        }
        rewardedPlayers.add(playerId);

        Reward reward = buildReward(eventType);

        FoliaScheduler.runEntity(plugin, player, () -> {
            giveMoney(player, reward);
            runCommands(player, reward, eventType);
            giveItems(player, reward);
        });
    }

    private Reward buildReward(SkyEventType eventType) {
        List<String> commands = new ArrayList<>(config.getRewardCommands(eventType));
        List<Reward.ItemReward> items = config.getConfig()
                .getMapList("rewards." + eventType.name().toLowerCase().replace("_", "-") + ".items")
                .stream()
                .map(this::parseItemReward)
                .toList();

        int moneyMin = config.getRewardMoneyMin(eventType);
        int moneyMax = config.getRewardMoneyMax(eventType);

        return new Reward(moneyMin, moneyMax, commands, items);
    }

    private Reward.ItemReward parseItemReward(Map<?, ?> map) {
        String materialStr = (String) map.get("material");
        Material material = Material.matchMaterial(materialStr);
        if (material == null) material = Material.STONE;
        Object amountObj = map.get("amount");
        int amount = amountObj != null ? ((Number) amountObj).intValue() : 1;
        Object nameObj = map.get("name");
        String name = nameObj != null ? (String) nameObj : "";
        Object loreObj = map.get("lore");
        @SuppressWarnings("unchecked")
        List<String> lore = loreObj != null ? (List<String>) loreObj : new ArrayList<>();
        return new Reward.ItemReward(material, amount, name, lore);
    }

    private void giveMoney(Player player, Reward reward) {
        if (reward.getMoneyMax() > 0) {
            int amount = reward.getMoneyMin() + (int) (Math.random() * (reward.getMoneyMax() - reward.getMoneyMin() + 1));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eco give " + player.getName() + " " + amount);
        }
    }

    private void runCommands(Player player, Reward reward, SkyEventType eventType) {
        String eventId = "";
        for (String cmd : reward.getCommands()) {
            String processed = cmd.replace("%player%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString())
                    .replace("%event%", eventType.name())
                    .replace("%server%", config.serverId())
                    .replace("%eventid%", eventId);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processed);
        }
    }

    private void giveItems(Player player, Reward reward) {
        for (Reward.ItemReward item : reward.getItems()) {
            ItemStack stack = new ItemStack(item.getMaterial(), Math.max(1, item.getAmount()));
            if (item.getName() != null && !item.getName().isEmpty()) {
                org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(item.getName());
                    stack.setItemMeta(meta);
                }
            }
            player.getInventory().addItem(stack);
        }
    }

    public boolean isRewarded(UUID playerId) {
        return rewardedPlayers.contains(playerId);
    }

    public void reset() {
        rewardedPlayers.clear();
    }
}
