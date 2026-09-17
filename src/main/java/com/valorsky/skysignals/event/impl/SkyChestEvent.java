package com.valorsky.skysignals.event.impl;

import com.valorsky.skysignals.event.AbstractSkyEvent;
import com.valorsky.skysignals.model.EventState;
import com.valorsky.skysignals.model.SkyEventStatus;
import com.valorsky.skysignals.model.SkyEventType;
import com.valorsky.skysignals.notification.NotificationService;
import com.valorsky.skysignals.reward.RewardService;
import com.valorsky.skysignals.util.FoliaScheduler;
import com.valorsky.skysignals.util.PositionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public final class SkyChestEvent extends AbstractSkyEvent {

    private final NotificationService notificationService;
    private final RewardService rewardService;
    private final Logger logger;

    private Block chestBlock;
    private boolean claimed = false;
    private final Set<UUID> rewardedPlayers = new HashSet<>();

    public SkyChestEvent(EventState state, JavaPlugin plugin, NotificationService notificationService, RewardService rewardService) {
        super(state, plugin);
        this.notificationService = notificationService;
        this.rewardService = rewardService;
        this.logger = plugin.getLogger();
    }

    @Override
    public void start() {
        this.status = SkyEventStatus.ACTIVE;

        World world = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().getEnvironment() == World.Environment.NORMAL)
                .map(Player::getWorld)
                .findFirst()
                .orElse(Bukkit.getWorld("world"));

        if (world == null) {
            this.status = SkyEventStatus.CANCELLED;
            return;
        }

        Location chestLoc = findChestPosition(world);
        if (chestLoc == null) {
            this.status = SkyEventStatus.CANCELLED;
            return;
        }

        FoliaScheduler.runRegion(plugin, chestLoc, () -> {
            Block block = chestLoc.getBlock();
            block.setType(Material.CHEST);
            chestBlock = block;

            populateChest(block);

            notificationService.notifyEventStart(this);
            logger.info("Sky Chest spawned at " + chestLoc.getWorld().getName() +
                    " " + chestLoc.getBlockX() + ", " + chestLoc.getBlockY() + ", " + chestLoc.getBlockZ());
        });
    }

    private Location findChestPosition(World world) {
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().equals(world))
                .map(p -> (Player) p)
                .toList();
        if (players.isEmpty()) return null;

        Player player = players.get((int) (Math.random() * players.size()));
        for (int attempt = 0; attempt < 15; attempt++) {
            int offsetX = (int) (Math.random() * 40) - 20;
            int offsetZ = (int) (Math.random() * 40) - 20;
            Location candidate = player.getLocation().add(offsetX, 0, offsetZ);
            candidate.setY(world.getHighestBlockYAt(candidate.getBlockX(), candidate.getBlockZ()));
            if (PositionUtils.isSafe(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private void populateChest(Block block) {
        if (!(block.getState() instanceof Chest chest)) return;
        Inventory inventory = chest.getInventory();

        FileConfiguration config = plugin.getConfig();
        List<Map<?, ?>> loot = config.getMapList("sky-chest.loot");
        if (loot == null || loot.isEmpty()) {
            inventory.addItem(new ItemStack(Material.GOLD_INGOT, 3));
            inventory.addItem(new ItemStack(Material.DIAMOND, 1));
            return;
        }

        Random random = new Random();
        for (Map<?, ?> entry : loot) {
            String materialStr = (String) entry.get("material");
            Material material = Material.matchMaterial(materialStr);
            if (material == null) continue;

            Object weightObj = entry.get("weight");
            int weight = weightObj != null ? ((Number) weightObj).intValue() : 1;
            if (random.nextInt(100) < weight) {
                Object minObj = entry.get("amount-min");
                int min = minObj != null ? ((Number) minObj).intValue() : 1;
                Object maxObj = entry.get("amount-max");
                int max = maxObj != null ? ((Number) maxObj).intValue() : 1;
                int amount = min + random.nextInt(max - min + 1);
                inventory.addItem(new ItemStack(material, amount));
            }
        }
    }

    @Override
    public void tick() {
    }

    @Override
    public void stop() {
        if (chestBlock != null && chestBlock.getLocation().getWorld() != null) {
            FoliaScheduler.runRegion(plugin, chestBlock.getLocation(), () -> {
                if (chestBlock.getType() == Material.CHEST) {
                    chestBlock.getWorld().dropItemNaturally(chestBlock.getLocation(), new ItemStack(Material.CHEST));
                    chestBlock.setType(Material.AIR);
                }
            });
        }
    }

    @Override
    public void cancel() {
        super.cancel();
        stop();
    }

    public boolean isClaimed() {
        return claimed;
    }

    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }

    public Block getChestBlock() {
        return chestBlock;
    }

    public Set<UUID> getRewardedPlayers() {
        return rewardedPlayers;
    }
}
