package com.valorsky.skysignals.listener;

import com.valorsky.skysignals.event.impl.SkyChestEvent;
import com.valorsky.skysignals.event.SkyEventManager;
import com.valorsky.skysignals.model.SkyEvent;
import com.valorsky.skysignals.notification.NotificationService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class EventListener implements Listener {

    private final JavaPlugin plugin;
    private final SkyEventManager eventManager;

    public EventListener(JavaPlugin plugin, SkyEventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.CHEST) return;

        for (SkyEvent activeEvent : eventManager.getActiveEvents()) {
            if (activeEvent instanceof SkyChestEvent chestEvent) {
                Block chestBlock = chestEvent.getChestBlock();
                if (chestBlock != null && chestBlock.getLocation().equals(event.getClickedBlock().getLocation())) {
                    if (chestEvent.isClaimed()) {
                        event.setCancelled(true);
                        return;
                    }

                    if (chestEvent.getRewardedPlayers().contains(event.getPlayer().getUniqueId())) {
                        event.setCancelled(true);
                        return;
                    }

                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (chestEvent.getChestBlock() != null) {
                            chestEvent.getChestBlock().setType(Material.AIR);
                        }
                        chestEvent.setClaimed(true);
                    }, 1L);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        for (SkyEvent activeEvent : eventManager.getActiveEvents()) {
            if (activeEvent instanceof SkyChestEvent chestEvent) {
                Block chestBlock = chestEvent.getChestBlock();
                if (chestBlock != null && chestBlock.getLocation().equals(event.getBlock().getLocation())) {
                    if (!chestEvent.isClaimed()) {
                        event.setCancelled(true);
                    }
                    return;
                }
            }
        }
    }
}
