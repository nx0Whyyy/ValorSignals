package com.valorsky.skysignals.listener;

import com.valorsky.skysignals.event.impl.MobInvasionEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

public final class PlayerListener implements Listener {

    private final MobInvasionEvent mobInvasionEvent;

    public PlayerListener(MobInvasionEvent mobInvasionEvent) {
        this.mobInvasionEvent = mobInvasionEvent;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (mobInvasionEvent == null) return;

        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity living) {
            if (mobInvasionEvent.getSpawnTagKey() != null &&
                    living.getPersistentDataContainer().has(mobInvasionEvent.getSpawnTagKey(), PersistentDataType.BYTE)) {
                event.getDrops().clear();
                living.getPersistentDataContainer().remove(mobInvasionEvent.getSpawnTagKey());
            }
        }
    }
}
