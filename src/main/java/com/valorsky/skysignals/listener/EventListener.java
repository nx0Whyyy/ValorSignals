package com.valorsky.skysignals.listener;

import com.valorsky.skysignals.event.SkyEventManager;
import com.valorsky.skysignals.event.impl.MobInvasionEvent;
import com.valorsky.skysignals.event.impl.SkyChestEvent;
import com.valorsky.skysignals.model.SkyEvent;
import com.valorsky.skysignals.util.FoliaScheduler;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class EventListener implements Listener {

    private final JavaPlugin plugin;
    private final SkyEventManager eventManager;
    private final NamespacedKey eventKey;
    private final NamespacedKey chestKey;
    private final NamespacedKey mobKey;

    public EventListener(JavaPlugin plugin, SkyEventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.eventKey = new NamespacedKey(plugin, "skysignals_event");
        this.chestKey = new NamespacedKey(plugin, "skysignals_chest");
        this.mobKey = new NamespacedKey(plugin, "skysignals_mob");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.CHEST) return;

        Block clickedBlock = event.getClickedBlock();
        Chest chest = (Chest) clickedBlock.getState();

        // Check if this is a SkySignals chest
        String eventId = chest.getPersistentDataContainer().get(eventKey, PersistentDataType.STRING);
        if (eventId == null) return;

        // Find the corresponding SkyChestEvent
        for (SkyEvent activeEvent : eventManager.getActiveEvents()) {
            if (activeEvent.getId().toString().equals(eventId) && activeEvent instanceof SkyChestEvent chestEvent) {
                event.setCancelled(true);
                chestEvent.tryClaim(event.getPlayer()).thenAccept(claimed -> {
                    if (claimed && plugin.isEnabled()) FoliaScheduler.runRegion(plugin, chest.getLocation(), () -> {
                        if (clickedBlock.getState() instanceof Chest current && eventId.equals(current.getPersistentDataContainer().get(eventKey, PersistentDataType.STRING))) {
                            current.getInventory().clear();
                            clickedBlock.setType(Material.AIR);
                        }
                    });
                });
                return;
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // Prevent breaking SkySignals chests
        Block block = event.getBlock();
        if (block.getType() == Material.CHEST) {
            Chest chest = (Chest) block.getState();
            String eventId = chest.getPersistentDataContainer().get(eventKey, PersistentDataType.STRING);
            if (eventId != null) {
                event.setCancelled(true);
                return;
            }
        }

        // Prevent breaking near mob invasion spawns
        for (SkyEvent activeEvent : eventManager.getActiveEvents()) {
            if (activeEvent instanceof MobInvasionEvent) {
                Block eventBlock = block;
                boolean isProtected = false;
                if (isProtected) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Byte isEventMob = entity.getPersistentDataContainer().get(mobKey, PersistentDataType.BYTE);

        if (isEventMob != null) {
            Player killer = entity.getKiller();
            String eventId = entity.getPersistentDataContainer().get(eventKey, PersistentDataType.STRING);
            if (killer != null && eventId != null) {
                for (SkyEvent activeEvent : eventManager.getActiveEvents()) {
                    if (activeEvent instanceof MobInvasionEvent invasion && activeEvent.getId().toString().equals(eventId)) {
                        invasion.onMobKill(killer, entity);
                    }
                }
            }
            // Clear drops from event mobs
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }
}