package com.valorsky.skysignals.event.impl;

import com.valorsky.skysignals.event.AbstractSkyEvent;
import com.valorsky.skysignals.model.*;
import com.valorsky.skysignals.notification.NotificationService;
import com.valorsky.skysignals.particle.ParticleService;
import com.valorsky.skysignals.particle.BeamShape;
import com.valorsky.skysignals.reward.RewardService;
import com.valorsky.skysignals.sound.SoundService;
import com.valorsky.skysignals.util.FoliaScheduler;
import com.valorsky.skysignals.util.FoliaScheduler.TaskHandle;
import com.valorsky.skysignals.location.SafeLocationService;
import com.valorsky.skysignals.config.Config;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class SkyChestEvent extends AbstractSkyEvent {

    private final NotificationService notificationService;
    private final RewardService rewardService;
    private final ParticleService particleService;
    private final SoundService soundService;
    private final SafeLocationService locationService;
    private final Config config;
    private final Logger logger;

    private Location chestLocation;
    private Location beamStart;
    private Chest chestBlock;
    private TaskHandle beamTask;
    private TaskHandle descentTask;
    private TaskHandle unlockTask;
    private final AtomicBoolean hasLanded = new AtomicBoolean(false);
    private final AtomicBoolean claimed = new AtomicBoolean(false);
    private final AtomicBoolean isUnlocked = new AtomicBoolean(false);
    private final Set<UUID> claimedPlayers = ConcurrentHashMap.newKeySet();

    public SkyChestEvent(EventState state, JavaPlugin plugin, NotificationService notificationService,
                         RewardService rewardService, ParticleService particleService,
                         SoundService soundService, SafeLocationService locationService, Config config) {
        super(state, plugin);
        this.notificationService = notificationService;
        this.rewardService = rewardService;
        this.particleService = particleService;
        this.soundService = soundService;
        this.locationService = locationService;
        this.config = config;
        this.logger = plugin.getLogger();
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    protected void onAnnouncing() {
        this.status = SkyEventStatus.ANNOUNCING;
        findLocationAndAnnounce();
    }

    @Override
    protected void onWarning() {
        this.status = SkyEventStatus.WARNING;
        startDescent();
    }

    @Override
    protected void onActive() {
        this.status = SkyEventStatus.ACTIVE;
        startUnlockCountdown();
    }

    @Override
    protected void onCompleting() {
        this.status = SkyEventStatus.COMPLETING;
        if (chestLocation != null) FoliaScheduler.runRegion(plugin, chestLocation, this::unlockChest);
    }

    @Override
    protected void onFinished() {
        this.status = SkyEventStatus.FINISHED;
        removeChest();
    }

    @Override
    protected void onCancelled() {
        this.status = SkyEventStatus.CANCELLED;
        removeChest();
    }

    private void findLocationAndAnnounce() {
        locationService.findNearPlayersAsync(50, 200).whenComplete((location, error) -> {
            if (!plugin.isEnabled()) return;
            FoliaScheduler.runGlobal(plugin, () -> {
                if (getStatus() != SkyEventStatus.ANNOUNCING) return;
                if (error != null || location.isEmpty()) {
                    logger.warning("No safe loaded location for " + getType());
                    cancel();
                    return;
                }
                Location loc = location.get();
                chestLocation = loc;
            beamStart = loc.clone().add(0, 50, 0);
                notificationService.notifyEventPhase(this, "descending");
                soundService.playGlobal("sky_chest_descend");
            });
        });
    }

    @Override
    public boolean isReady() { return chestLocation != null; }

    private void startDescent() {
        if (chestLocation == null) return;

        beamTask = FoliaScheduler.runRegionTimer(plugin, chestLocation, () -> {
            if (chestLocation == null || hasLanded.get()) {
                if (beamTask != null) beamTask.cancel();
                return;
            }
            List<Player> audience = getNearbyPlayers(chestLocation, 48);
            if (!audience.isEmpty()) {
                BeamShape beam = new BeamShape(beamStart, chestLocation, 1.0, 20);
                particleService.spawnBeam(beamStart, chestLocation, Particle.END_ROD, 1.0, 20, 0.01, audience);
            }
        }, 0L, 2L);

        descentTask = FoliaScheduler.runRegionTimer(plugin, chestLocation, new Runnable() {
            double progress = 0;
            @Override
            public void run() {
                if (hasLanded.get()) {
                    if (descentTask != null) descentTask.cancel();
                    return;
                }
                progress += 0.05;
                if (progress >= 1.0) {
                    progress = 1.0;
                    hasLanded.set(true);
                    landChest();
                    descentTask.cancel();
                    return;
                }

                Location current = beamStart.clone().add(0, -50 * progress, 0);
                List<Player> audience = getNearbyPlayers(current, 48);
                if (!audience.isEmpty()) {
                    particleService.spawnCircle(current, Particle.HAPPY_VILLAGER, 2, 10, 0.05, audience);
                }
            }
        }, 0L, 1L);
    }

    private void landChest() {
        if (chestLocation == null || chestLocation.getWorld() == null) return;

        if (cancelled || getStatus() == SkyEventStatus.FINISHED) return;
        Block block = chestLocation.getBlock();
        if (!block.isEmpty()) { cancel(); return; }
        block.setType(Material.CHEST);
        Chest chest = (Chest) block.getState();
        chestBlock = chest;

        PersistentDataContainer pdc = chest.getPersistentDataContainer();
        pdc.set(new NamespacedKey(plugin, "skysignals_event"), PersistentDataType.STRING, id.toString());
        pdc.set(new NamespacedKey(plugin, "skysignals_chest"), PersistentDataType.BYTE, (byte) 1);

        chest.update();
        fillChestWithLoot();

        List<Player> audience = getNearbyPlayers(chestLocation, 48);
        if (!audience.isEmpty()) {
            particleService.spawnExplosion(chestLocation, Particle.HAPPY_VILLAGER, 3, 20, 0.1, audience);
            particleService.spawnRing(chestLocation, Particle.END_ROD, 0, 5, 20, 0.1, audience);
        }
        soundService.play("sky_chest_land", chestLocation, audience);

        notificationService.notifyEventPhase(this, "landing");
    }

    private void fillChestWithLoot() {
        if (chestBlock == null) return;

        Inventory inv = chestBlock.getInventory();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
        }
    }

    private void startUnlockCountdown() {
        notificationService.notifyEventPhase(this, "unlocking");
        soundService.playGlobal("sky_chest_unlock");

        int[] countdown = {10};
        unlockTask = FoliaScheduler.runRegionTimer(plugin, chestLocation, () -> {
            if (isUnlocked.get()) {
                unlockTask.cancel();
                return;
            }
            countdown[0]--;
            if (countdown[0] <= 0) {
                unlockChest();
                unlockTask.cancel();
            }
        }, 20L, 20L);
    }

    private void unlockChest() {
        if (isUnlocked.compareAndSet(false, true)) {
            if (chestBlock != null) {
                Inventory inv = chestBlock.getInventory();
                inv.clear();
            }

            List<Player> audience = getNearbyPlayers(chestLocation, 48);
            if (!audience.isEmpty()) {
                particleService.spawnCircle(chestLocation, Particle.HAPPY_VILLAGER, 3, 20, 0.1, audience);
            }
            soundService.play("sky_chest_claim", chestLocation, audience);

            notificationService.notifyEventPhase(this, "unlocked");
        }
    }

    public java.util.concurrent.CompletableFuture<Boolean> tryClaim(Player player) {
        if (!isUnlocked.get()) {
            player.sendMessage(Component.text("La caisse est encore verrouillée !"));
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        if (!claimed.compareAndSet(false, true)) {
            player.sendMessage(Component.text("Cette caisse a déjà été récupérée."));
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }
        if (!rewardService.rewardsAllowed(id)) return java.util.concurrent.CompletableFuture.completedFuture(true);
        return rewardService.giveRewards(id, SkyEventType.SKY_CHEST, player.getUniqueId(), serverId)
            .handle((result, error) -> {
                boolean success = error == null && result.success();
                if (!success) {
                    claimed.set(false);
                    FoliaScheduler.runEntity(plugin, player, () -> player.sendMessage(Component.text("Récompense indisponible, réessaie dans un instant.")));
                } else {
                    FoliaScheduler.runEntity(plugin, player, () -> soundService.play("success", player.getLocation(), List.of(player)));
                }
                return success;
            });
    }

    private void removeChest() {
        if (chestLocation != null) {
            Location loc = chestLocation;
            FoliaScheduler.runRegion(plugin, loc, () -> {
                if (loc.getBlock().getState() instanceof Chest chest && id.toString().equals(
                        chest.getPersistentDataContainer().get(new NamespacedKey(plugin, "skysignals_event"), PersistentDataType.STRING))) {
                    chest.getInventory().clear();
                    loc.getBlock().setType(Material.AIR);
                }
            });
        }

        if (beamTask != null) beamTask.cancel();
        if (descentTask != null) descentTask.cancel();
        if (unlockTask != null) unlockTask.cancel();
    }

    private List<Player> getNearbyPlayers(Location center, double radius) {
        if (center == null || center.getWorld() == null) return List.of();
        double radiusSq = radius * radius;
        List<Player> result = new ArrayList<>();
        for (Player player : center.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= radiusSq) {
                result.add(player);
            }
        }
        return result;
    }

    @Override
    public void tick(long elapsedSeconds) {
        super.tick(elapsedSeconds);
    }

    @Override
    public void stop() {
        removeChest();
    }

    @Override
    public void cancel() {
        super.cancel();
        stop();
    }

    @Override
    protected Map<String, Object> getExtraData() {
        return Map.of(
            "chestX", chestLocation != null ? chestLocation.getBlockX() : 0,
            "chestY", chestLocation != null ? chestLocation.getBlockY() : 0,
            "chestZ", chestLocation != null ? chestLocation.getBlockZ() : 0,
            "unlocked", isUnlocked.get()
        );
    }
}