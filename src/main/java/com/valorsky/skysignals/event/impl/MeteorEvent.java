package com.valorsky.skysignals.event.impl;

import com.valorsky.skysignals.event.AbstractSkyEvent;
import com.valorsky.skysignals.model.*;
import com.valorsky.skysignals.notification.NotificationService;
import com.valorsky.skysignals.particle.ParticleService;
import com.valorsky.skysignals.reward.RewardService;
import com.valorsky.skysignals.sound.SoundService;
import com.valorsky.skysignals.util.FoliaScheduler;
import com.valorsky.skysignals.util.FoliaScheduler.TaskHandle;
import com.valorsky.skysignals.config.Config;
import com.valorsky.skysignals.location.SafeLocationService;
import org.bukkit.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class MeteorEvent extends AbstractSkyEvent {

    private final NotificationService notificationService;
    private final RewardService rewardService;
    private final ParticleService particleService;
    private final SoundService soundService;
    private final SafeLocationService locationService;
    private final Config config;
    private final Logger logger;

    private Location meteorStart;
    private volatile Location meteorTarget;
    private volatile Location forcedTarget;
    private ItemDisplay meteorDisplay;
    private ItemDisplay impactDisplay;
    private TaskHandle meteorTask;
    private TaskHandle trailTask;
    private TaskHandle restorationTask;
    private float modelRotation;
    private int descentTick;
    private final AtomicBoolean hasLanded = new AtomicBoolean(false);
    private final Set<UUID> notifiedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> rewardedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<String, BlockSnapshot> craterSnapshot = new ConcurrentHashMap<>();
    private String direction = "unknown";

    public MeteorEvent(EventState state, JavaPlugin plugin, NotificationService notificationService,
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
        this.status = SkyEventStatus.SCHEDULED;
        this.phase = SkyEventPhase.SCHEDULED;
    }

    @Override
    protected void onAnnouncing() {
        this.status = SkyEventStatus.ANNOUNCING;
        findTargetAndPrepare();
    }

    @Override
    protected void onWarning() {
        this.status = SkyEventStatus.WARNING;
        startMeteorDescent();
    }

    @Override
    protected void onActive() {
        this.status = SkyEventStatus.ACTIVE;
    }

    @Override
    protected void onCompleting() {
        this.status = SkyEventStatus.COMPLETING;
        handleImpact();
    }

    @Override
    protected void onFinished() {
        this.status = SkyEventStatus.FINISHED;
        cleanupMeteor();
    }

    @Override
    protected void onCancelled() {
        this.status = SkyEventStatus.CANCELLED;
        cleanupMeteor();
    }

    private void findTargetAndPrepare() {
        Location testTarget = forcedTarget;
        if (testTarget != null && testTarget.getWorld() != null) {
            meteorTarget = testTarget.clone();
            soundService.playGlobal("meteor_start");
            return;
        }
        locationService.findNearPlayersAsync(10, 30).whenComplete((location, error) -> {
            if (!plugin.isEnabled()) return;
            FoliaScheduler.runGlobal(plugin, () -> {
                if (getStatus() != SkyEventStatus.ANNOUNCING) return;
                if (error != null || location.isEmpty()) {
                    logger.warning("No safe loaded location for " + getType());
                    cancel();
                    return;
                }
                Location loc = location.get();
                meteorTarget = loc;

                soundService.playGlobal("meteor_start");
            });
        });
    }

    @Override
    public List<EventLocation> getEventLocations() {
        Location location = meteorTarget;
        return location == null || location.getWorld() == null ? List.of()
                : List.of(EventLocation.at(location, "Point d’impact", 0));
    }

    @Override
    public boolean isReady() { return meteorTarget != null; }

    public Optional<Location> getImpactLocation() {
        Location target = meteorTarget;
        return target == null ? Optional.empty() : Optional.of(target.clone());
    }

    public void setForcedTarget(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Meteor test target must belong to a world");
        }
        this.forcedTarget = location.clone();
    }

    private void startMeteorDescent() {
        if (meteorTarget == null) return;
        Location requestedTarget = meteorTarget.clone();
        FoliaScheduler.runRegion(plugin, requestedTarget, () -> {
            meteorTarget = resolveGround(requestedTarget);
            boolean testAtPlayer = forcedTarget != null;
            double angle = random.nextDouble() * Math.PI * 2.0;
            double horizontalDistance = testAtPlayer ? 0.0 : config.getMeteorHorizontalDistance();
            double startHeight = testAtPlayer
                    ? Math.min(config.getMeteorStartHeight(), Math.max(24.0, config.viewDistance() * 0.75))
                    : config.getMeteorStartHeight();
            Location start = meteorTarget.clone().add(
                    Math.cos(angle) * horizontalDistance,
                    startHeight,
                    Math.sin(angle) * horizontalDistance);
            FoliaScheduler.runRegion(plugin, start, () -> spawnDescendingMeteor(start));
        });
    }

    private Location resolveGround(Location requested) {
        World world = requested.getWorld();
        int x = requested.getBlockX();
        int z = requested.getBlockZ();
        int startY = Math.min(world.getMaxHeight() - 1, requested.getBlockY());
        for (int y = startY; y >= world.getMinHeight(); y--) {
            if (world.getBlockAt(x, y, z).getType().isSolid()) {
                return new Location(world, x + 0.5, y + 1.0, z + 0.5);
            }
        }
        return requested.clone();
    }

    private void spawnDescendingMeteor(Location start) {
            if (cancelled || getStatus() == SkyEventStatus.FINISHED) return;
            meteorStart = start;
            descentTick = 0;
            meteorDisplay = spawnMeteorDisplay(meteorStart);

            soundService.play("meteor_warning", meteorTarget, getNearbyPlayers(meteorTarget, 160));

            trailTask = FoliaScheduler.runEntityRepeating(plugin, meteorDisplay, () -> {
                if (meteorDisplay == null || !meteorDisplay.isValid() || hasLanded.get()) {
                    if (trailTask != null) trailTask.cancel();
                    return;
                }
                Location loc = meteorDisplay.getLocation();
                List<Player> audience = getNearbyPlayers(loc, 160);
                if (!audience.isEmpty() && config.meteorEffectsEnabled()) {
                    Vector towardStart = meteorStart.toVector().subtract(loc.toVector());
                    if (towardStart.lengthSquared() > 0.001) {
                        Location tail = loc.clone().add(towardStart.normalize().multiply(config.getMeteorTrailLength()));
                        particleService.spawnMeteorTrail(tail, loc, Particle.FLAME,
                                config.getMeteorFlameCount(), 0.02, audience);
                    }
                    double radius = config.getMeteorEffectRadius();
                    particleService.spawnCircle(loc, Particle.FLAME, radius,
                            config.getMeteorFlameCount(), 0.02, audience);
                    particleService.spawnSphere(loc, Particle.SMOKE, radius * 0.75,
                            config.getMeteorSmokeCount(), 0.015, audience);
                    particleService.spawnSphere(loc, Particle.LAVA, radius * 0.55,
                            config.getMeteorLavaCount(), 0.01, audience);
                }
            }, 1L, 2L);

            meteorTask = FoliaScheduler.runEntityRepeating(plugin, meteorDisplay, () -> {
                if (meteorDisplay == null || !meteorDisplay.isValid()) {
                    cleanupFlight();
                    return;
                }
                Location loc = meteorDisplay.getLocation();

                int totalTicks = config.getMeteorDescentDuration() * 20;
                descentTick++;
                rotateMeteor();
                double progress = Math.min(1.0, descentTick / (double) totalTicks);
                // The supplied flight cube occupies 14/16 of a model block. Position its
                // lowest rendered point exactly on the resolved solid surface.
                double renderedHalfHeight = config.getMeteorModelScale() * 0.4375;
                Location impactPoint = meteorTarget.clone().add(0, renderedHalfHeight - 1.0, 0);
                Location next = meteorStart.clone().add(
                        (impactPoint.getX() - meteorStart.getX()) * progress,
                        (impactPoint.getY() - meteorStart.getY()) * progress,
                        (impactPoint.getZ() - meteorStart.getZ()) * progress);
                if (progress >= 1.0) {
                    ItemDisplay display = meteorDisplay;
                    meteorTask.cancel();
                    display.teleportAsync(impactPoint).thenRun(this::handleImpact);
                } else {
                    meteorDisplay.teleportAsync(next);
                }
            }, 1L, 1L);

            logger.info("Meteor descent started at " + meteorStart.getWorld().getName());
    }

    private ItemDisplay spawnMeteorDisplay(Location location) {
        return location.getWorld().spawn(location, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setGlowing(true);
            display.setGlowColorOverride(Color.fromRGB(255, 85, 0));
            display.setTeleportDuration(1);
            display.setInterpolationDuration(1);
            display.setViewRange(4.0f);
            display.setDisplayWidth(24.0f);
            display.setDisplayHeight(24.0f);
            display.setShadowRadius(4.0f);
            display.setShadowStrength(0.8f);

            ItemStack item = new ItemStack(config.getMeteorModelItem());
            ItemMeta meta = item.getItemMeta();
            if (config.isMeteorModelEnabled()) {
                NamespacedKey model = NamespacedKey.fromString(config.getMeteorItemModel());
                if (model != null) meta.setItemModel(model);
                else logger.warning("Invalid meteor item model key: " + config.getMeteorItemModel());
            }
            item.setItemMeta(meta);
            display.setItemStack(item);

            float scale = config.getMeteorModelScale();
            display.setTransformation(new Transformation(
                    new Vector3f(), new Quaternionf(),
                    new Vector3f(scale, scale, scale), new Quaternionf()));
        });
    }

    private ItemDisplay spawnImpactDisplay(Location location) {
        return location.getWorld().spawn(location, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setBillboard(Display.Billboard.FIXED);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setViewRange(4.0f);
            display.setDisplayWidth(24.0f);
            display.setDisplayHeight(12.0f);

            ItemStack item = new ItemStack(config.getMeteorModelItem());
            ItemMeta meta = item.getItemMeta();
            NamespacedKey model = NamespacedKey.fromString(config.getMeteorImpactItemModel());
            if (model != null) meta.setItemModel(model);
            else logger.warning("Invalid meteor impact model key: " + config.getMeteorImpactItemModel());
            item.setItemMeta(meta);
            display.setItemStack(item);

            float scale = config.getMeteorImpactScale();
            display.setTransformation(new Transformation(
                    new Vector3f(), new Quaternionf(),
                    new Vector3f(scale, scale, scale), new Quaternionf()));
        });
    }

    private void playImpactModelAnimation() {
        scheduleImpactStage("skysignals:meteor_impact_2", 10L);
        scheduleImpactStage("skysignals:meteor_impact_3", 20L);
        scheduleImpactStage("skysignals:meteor_impact_4", 30L);
    }

    private void scheduleImpactStage(String modelKey, long delayTicks) {
        ItemDisplay display = impactDisplay;
        if (display == null) return;
        FoliaScheduler.runEntityDelayed(plugin, display, () -> {
            if (!display.isValid() || display != impactDisplay) return;
            ItemStack item = display.getItemStack();
            ItemMeta meta = item.getItemMeta();
            NamespacedKey model = NamespacedKey.fromString(modelKey);
            if (model == null) return;
            meta.setItemModel(model);
            item.setItemMeta(meta);
            display.setItemStack(item);
        }, delayTicks);
    }

    private void rotateMeteor() {
        modelRotation += config.getMeteorRotationSpeed();
        float radians = (float) Math.toRadians(modelRotation);
        float scale = config.getMeteorModelScale();
        meteorDisplay.setTransformation(new Transformation(
                new Vector3f(), new Quaternionf().rotateXYZ(radians * 0.35f, radians, radians * 0.15f),
                new Vector3f(scale, scale, scale), new Quaternionf()));
    }

    private void handleImpact() {
        if (meteorTarget == null || !hasLanded.compareAndSet(false, true)) return;
        FoliaScheduler.runRegion(plugin, meteorTarget, this::applyImpact);
    }

    private void applyImpact() {
        if (cancelled) return;
        World world = meteorTarget.getWorld();
        if (world != null) {
            cleanupFlight();
            impactDisplay = spawnImpactDisplay(meteorTarget.clone().add(0.5, 0.4, 0.5));
            playImpactModelAnimation();
            List<Player> audience = getNearbyPlayers(meteorTarget, 48);
            if (!audience.isEmpty()) {
                particleService.spawnExplosion(meteorTarget, Particle.EXPLOSION, 5, 20, 0.1, audience);
                particleService.spawnRing(meteorTarget, Particle.SMOKE, 0, 8, 30, 0.1, audience);
            }

            soundService.play("meteor_impact", meteorTarget, audience);

            if (config.getMeteorTerrainDestruction()) {
                if (isWorldGuardProtected(meteorTarget)) {
                    logger.info("Meteor crater skipped inside a WorldGuard region at " + formatLocation(meteorTarget));
                } else {
                    createCrater();
                }
            }

            for (Player player : world.getPlayers()) {
                double distance = player.getLocation().distance(meteorTarget);
                if (distance <= 20) {
                    if (rewardedPlayers.add(player.getUniqueId())) {
                        rewardService.giveRewards(id, SkyEventType.METEOR, player.getUniqueId(), serverId);
                    }
                }
            }
        }

        notificationService.notifyEventPhase(this, "impact");
    }

    private boolean isWorldGuardProtected(Location location) {
        if (!config.meteorProtectWorldGuardRegions()
                || !Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return false;
        try {
            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object worldGuard = worldGuardClass.getMethod("getInstance").invoke(null);
            Object platform = worldGuardClass.getMethod("getPlatform").invoke(worldGuard);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = container.getClass().getMethod("createQuery").invoke(container);
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object adapted = adapter.getMethod("adapt", Location.class).invoke(null, location);
            var method = Arrays.stream(query.getClass().getMethods())
                    .filter(candidate -> candidate.getName().equals("getApplicableRegions")
                            && candidate.getParameterCount() == 1
                            && candidate.getParameterTypes()[0].isInstance(adapted))
                    .findFirst().orElseThrow();
            Object regions = method.invoke(query, adapted);
            return ((Number) regions.getClass().getMethod("size").invoke(regions)).intValue() > 0;
        } catch (Exception error) {
            logger.log(java.util.logging.Level.WARNING,
                    "Could not verify WorldGuard protection; crater creation was blocked for safety", error);
            return true;
        }
    }

    private void createCrater() {
        int radius = config.getMeteorCraterRadius();
        int maxDepth = config.getMeteorCraterDepth();
        int surfaceY = meteorTarget.getBlockY() - 1;
        World world = meteorTarget.getWorld();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > radius) continue;
                int depth = Math.max(1, (int) Math.round(maxDepth * (1.0 - distance / (radius + 0.5))));
                int floorY = surfaceY - depth;
                int x = meteorTarget.getBlockX() + dx;
                int z = meteorTarget.getBlockZ() + dz;
                Location column = new Location(world, x, floorY, z);
                FoliaScheduler.runRegion(plugin, column, () -> {
                    for (int y = floorY + 1; y <= surfaceY + 1; y++) {
                        Block block = world.getBlockAt(x, y, z);
                        captureOriginal(block);
                        block.setType(Material.AIR, false);
                    }
                    Block floor = world.getBlockAt(x, floorY, z);
                    captureOriginal(floor);
                    floor.setType(craterFloorMaterial(distance), false);
                });
            }
        }
        Location core = new Location(world, meteorTarget.getBlockX(), surfaceY - maxDepth, meteorTarget.getBlockZ());
        FoliaScheduler.runRegionDelayed(plugin, core, () -> buildCoreAndChest(core), 3L);
        scheduleCraterRestoration();
    }

    private Material craterFloorMaterial(double distance) {
        if (distance <= 1.5) return Material.OBSIDIAN;
        if (distance <= 2.75) return random.nextBoolean() ? Material.CRYING_OBSIDIAN : Material.MAGMA_BLOCK;
        Material[] rock = {Material.BLACKSTONE, Material.BASALT, Material.TUFF, Material.DEEPSLATE};
        return rock[random.nextInt(rock.length)];
    }

    private void buildCoreAndChest(Location core) {
        World world = core.getWorld();
        int y = core.getBlockY();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                Location ringBlock = new Location(world, core.getBlockX() + dx, y, core.getBlockZ() + dz);
                FoliaScheduler.runRegion(plugin, ringBlock, () -> {
                    captureOriginal(ringBlock.getBlock());
                    ringBlock.getBlock().setType(Material.OBSIDIAN, false);
                });
            }
        }
        Block nucleus = world.getBlockAt(core.getBlockX(), y, core.getBlockZ());
        captureOriginal(nucleus);
        nucleus.setType(Material.CRYING_OBSIDIAN, false);
        if (!config.meteorChestEnabled()) return;
        Block chestBlock = world.getBlockAt(core.getBlockX(), y + 1, core.getBlockZ());
        captureOriginal(chestBlock);
        chestBlock.setType(Material.CHEST, false);
        if (chestBlock.getState() instanceof Chest chest) {
            chest.setCustomName("Noyau de météorite");
            List<Integer> slots = new ArrayList<>();
            for (int slot = 0; slot < chest.getInventory().getSize(); slot++) slots.add(slot);
            Collections.shuffle(slots, random);
            int index = 0;
            for (Map.Entry<Material, Integer> entry : config.getMeteorChestLoot().entrySet()) {
                if (index >= slots.size()) break;
                chest.getInventory().setItem(slots.get(index++), new ItemStack(entry.getKey(), entry.getValue()));
            }
            chest.update(true, false);
        }
    }

    private void captureOriginal(Block block) {
        String key = block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
        craterSnapshot.computeIfAbsent(key, ignored -> {
            ItemStack[] contents = block.getState() instanceof Container container
                    ? Arrays.stream(container.getInventory().getContents())
                    .map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new)
                    : null;
            return new BlockSnapshot(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(),
                    block.getBlockData().clone(), contents);
        });
    }

    private void scheduleCraterRestoration() {
        if (!config.meteorRestorationEnabled() || restorationTask != null) return;
        long delayTicks = config.getMeteorRestorationDelayMinutes() * 60L * 20L;
        restorationTask = FoliaScheduler.runGlobalDelayed(plugin, this::restoreCrater, delayTicks);
    }

    private void restoreCrater() {
        List<BlockSnapshot> snapshots = new ArrayList<>(craterSnapshot.values());
        craterSnapshot.clear();
        for (BlockSnapshot snapshot : snapshots) {
            World world = Bukkit.getWorld(snapshot.worldId());
            if (world == null) continue;
            Location location = new Location(world, snapshot.x(), snapshot.y(), snapshot.z());
            FoliaScheduler.runRegion(plugin, location, () -> {
                Block block = location.getBlock();
                block.setBlockData(snapshot.blockData(), false);
                if (snapshot.contents() != null && block.getState() instanceof Container container) {
                    container.getInventory().setContents(Arrays.stream(snapshot.contents())
                            .map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new));
                    container.update(true, false);
                }
            });
        }
        logger.info("Restored " + snapshots.size() + " blocks from meteor crater " + id);
    }

    private record BlockSnapshot(UUID worldId, int x, int y, int z,
                                 BlockData blockData, ItemStack[] contents) { }

    private String formatLocation(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + ","
                + location.getBlockY() + "," + location.getBlockZ();
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

    private void cleanupMeteor() {
        cleanupFlight();
        if (impactDisplay != null) {
            ItemDisplay display = impactDisplay;
            FoliaScheduler.runEntity(plugin, display, display::remove);
            impactDisplay = null;
        }
    }

    private void cleanupFlight() {
        if (meteorDisplay != null) {
            ItemDisplay display = meteorDisplay;
            FoliaScheduler.runEntity(plugin, display, display::remove);
        }
        meteorDisplay = null;
        if (meteorTask != null) meteorTask.cancel();
        if (trailTask != null) trailTask.cancel();
    }

    @Override
    public void tick(long elapsedSeconds) {
        super.tick(elapsedSeconds);
    }

    @Override
    public void stop() {
        cleanupMeteor();
    }

    @Override
    public void cancel() {
        super.cancel();
        stop();
    }

    @Override
    protected Map<String, Object> getExtraData() {
        return Map.of(
            "targetX", meteorTarget != null ? meteorTarget.getBlockX() : 0,
            "targetY", meteorTarget != null ? meteorTarget.getBlockY() : 0,
            "targetZ", meteorTarget != null ? meteorTarget.getBlockZ() : 0,
            "direction", direction
        );
    }
}
