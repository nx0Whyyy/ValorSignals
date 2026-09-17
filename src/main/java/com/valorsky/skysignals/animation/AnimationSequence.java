package com.valorsky.skysignals.animation;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class AnimationSequence {
    private final JavaPlugin plugin;
    private final List<AnimationStep> steps = new ArrayList<>();

    private AnimationSequence(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static Builder builder(JavaPlugin plugin) {
        return new Builder(plugin);
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public List<AnimationStep> getSteps() {
        return List.copyOf(steps);
    }

    public void addStep(AnimationStep step) {
        steps.add(step);
    }

    public static final class Builder {
        private final JavaPlugin plugin;
        private final AnimationSequence sequence;

        private Builder(JavaPlugin plugin) {
            this.plugin = plugin;
            this.sequence = new AnimationSequence(plugin);
        }

        public Builder delay(int ticks) {
            sequence.addStep(new DelayStep(ticks));
            return this;
        }

        public Builder particle(com.valorsky.skysignals.animation.ParticleStep step) {
            sequence.addStep(step);
            return this;
        }

        public Builder sound(com.valorsky.skysignals.animation.SoundStep step) {
            sequence.addStep(step);
            return this;
        }

        public Builder message(com.valorsky.skysignals.animation.MessageStep step) {
            sequence.addStep(step);
            return this;
        }

        public Builder title(com.valorsky.skysignals.animation.TitleStep step) {
            sequence.addStep(step);
            return this;
        }

        public Builder actionBar(com.valorsky.skysignals.animation.ActionBarStep step) {
            sequence.addStep(step);
            return this;
        }

        public Builder bossBar(com.valorsky.skysignals.animation.BossBarStep step) {
            sequence.addStep(step);
            return this;
        }

        public Builder entitySpawn(com.valorsky.skysignals.animation.EntitySpawnStep step) {
            sequence.addStep(step);
            return this;
        }

        public Builder entityMove(com.valorsky.skysignals.animation.EntityMoveStep step) {
            sequence.addStep(step);
            return this;
        }

        public Builder entityRemove(com.valorsky.skysignals.animation.EntityRemoveStep step) {
            sequence.addStep(step);
            return this;
        }

        public Builder blockEffect(com.valorsky.skysignals.animation.BlockEffectStep step) {
            sequence.addStep(step);
            return this;
        }

        public Builder custom(com.valorsky.skysignals.animation.CustomStep step) {
            sequence.addStep(step);
            return this;
        }

        public AnimationSequence build() {
            return sequence;
        }
    }
}