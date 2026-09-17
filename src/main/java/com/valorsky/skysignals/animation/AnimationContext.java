package com.valorsky.skysignals.animation;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import com.valorsky.skysignals.util.FoliaScheduler.TaskHandle;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class AnimationContext {
    private final Location origin;
    private final List<Player> audience;
    private final AnimationSequence sequence;
    private final AtomicInteger currentStep = new AtomicInteger(0);
    private TaskHandle task;
    private boolean cancelled = false;

    public AnimationContext(Location origin, List<Player> audience, AnimationSequence sequence) {
        this.origin = origin;
        this.audience = List.copyOf(audience);
        this.sequence = sequence;
    }

    public Location getOrigin() {
        return origin;
    }

    public List<Player> getAudience() {
        return audience;
    }

    public AnimationSequence getSequence() {
        return sequence;
    }

    public int getCurrentStep() {
        return currentStep.get();
    }

    public boolean nextStep() {
        return currentStep.incrementAndGet() < sequence.getSteps().size();
    }

    public TaskHandle getTask() {
        return task;
    }

    public void setTask(TaskHandle task) {
        this.task = task;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        this.cancelled = true;
        if (task != null) {
            task.cancel();
        }
    }
}