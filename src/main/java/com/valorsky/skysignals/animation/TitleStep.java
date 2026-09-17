package com.valorsky.skysignals.animation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public record TitleStep(
    Component title,
    Component subtitle,
    int fadeIn,
    int stay,
    int fadeOut
) implements AnimationStep {

    public TitleStep(Component title, Component subtitle) {
        this(title, subtitle, 10, 40, 20);
    }

    @Override
    public void execute(AnimationContext context) {
        Title titleObj = Title.title(title, subtitle, Title.Times.times(
            Duration.of(fadeIn * 50L, ChronoUnit.MILLIS),
            Duration.of(stay * 50L, ChronoUnit.MILLIS),
            Duration.of(fadeOut * 50L, ChronoUnit.MILLIS)
        ));
        for (Player player : context.getAudience()) {
            player.showTitle(titleObj);
        }
    }
}