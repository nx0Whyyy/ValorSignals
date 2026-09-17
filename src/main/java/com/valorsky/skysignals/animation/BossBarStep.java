package com.valorsky.skysignals.animation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.entity.Player;

public record BossBarStep(
    Component message,
    float progress,
    BossBar.Color color,
    BossBar.Overlay overlay
) implements AnimationStep {

    public BossBarStep(Component message, float progress) {
        this(message, progress, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);
    }

    @Override
    public void execute(AnimationContext context) {
        BossBar bossBar = BossBar.bossBar(message, progress, color, overlay);
        for (Player player : context.getAudience()) {
            player.showBossBar(bossBar);
        }
    }
}