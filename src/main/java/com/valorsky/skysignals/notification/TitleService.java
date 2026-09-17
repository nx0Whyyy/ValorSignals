package com.valorsky.skysignals.notification;

import com.valorsky.skysignals.config.Config;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.List;

public final class TitleService {

    private final JavaPlugin plugin;
    private final Config config;

    public TitleService(JavaPlugin plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void sendTitle(Component title, Component subtitle, List<Player> audience) {
        if (!config.titlesEnabled()) return;

        Title titleObj = Title.title(
            title,
            subtitle,
            Title.Times.times(
                Duration.ofMillis(config.titleFadeIn() * 50L),
                Duration.ofMillis(config.titleStay() * 50L),
                Duration.ofMillis(config.titleFadeOut() * 50L)
            )
        );

        for (Player player : audience) {
            player.showTitle(titleObj);
        }
    }

    public void sendTitle(Component title, Component subtitle, int fadeIn, int stay, int fadeOut, List<Player> audience) {
        if (!config.titlesEnabled()) return;

        Title titleObj = Title.title(
            title,
            subtitle,
            Title.Times.times(
                Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L)
            )
        );

        for (Player player : audience) {
            player.showTitle(titleObj);
        }
    }

    public void clearTitle(List<Player> audience) {
        for (Player player : audience) {
            player.clearTitle();
        }
    }

    public void resetTitle(List<Player> audience) {
        for (Player player : audience) {
            player.resetTitle();
        }
    }
}