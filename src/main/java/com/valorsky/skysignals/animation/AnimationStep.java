package com.valorsky.skysignals.animation;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public sealed interface AnimationStep permits
    ParticleStep,
    SoundStep,
    MessageStep,
    TitleStep,
    ActionBarStep,
    BossBarStep,
    EntitySpawnStep,
    EntityMoveStep,
    EntityRemoveStep,
    BlockEffectStep,
    DelayStep,
    CustomStep {

    void execute(AnimationContext context);

    default String getDescription() {
        return getClass().getSimpleName();
    }
}