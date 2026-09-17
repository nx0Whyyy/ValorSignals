package com.valorsky.skysignals.animation;

import com.valorsky.skysignals.util.FoliaScheduler;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class AnimationService {

    private final JavaPlugin plugin;

    public AnimationService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void play(AnimationSequence sequence, Location origin, List<Player> audience) {
        if (audience.isEmpty() || sequence.getSteps().isEmpty()) return;

        AnimationContext context = new AnimationContext(origin, audience, sequence);
        runStep(context);
    }

    private void runStep(AnimationContext context) {
        if (context.isCancelled()) return;

        int stepIndex = context.getCurrentStep();
        if (stepIndex >= context.getSequence().getSteps().size()) {
            return;
        }

        AnimationStep step = context.getSequence().getSteps().get(stepIndex);

        if (step instanceof DelayStep delayStep) {
            long delay = delayStep.ticks();
            FoliaScheduler.TaskHandle task = FoliaScheduler.runGlobalTimer(plugin, () -> {
                if (context.nextStep()) {
                    runStep(context);
                }
            }, delay, 1);
            context.setTask(null); // TaskHandle doesn't match BukkitTask
        } else {
            FoliaScheduler.runGlobal(plugin, () -> {
                try {
                    step.execute(context);
                } catch (Exception e) {
                    plugin.getLogger().warning("Animation step failed: " + e.getMessage());
                }
                if (context.nextStep()) {
                    runStep(context);
                }
            });
        }
    }

    public void playForAll(AnimationSequence sequence, Location origin) {
        List<Player> audience = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getWorld().equals(origin.getWorld())) {
                audience.add(player);
            }
        }
        play(sequence, origin, audience);
    }

    public void playNearby(AnimationSequence sequence, Location origin, double radius) {
        List<Player> audience = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getWorld().equals(origin.getWorld()) &&
                player.getLocation().distanceSquared(origin) <= radius * radius) {
                audience.add(player);
            }
        }
        play(sequence, origin, audience);
    }
}