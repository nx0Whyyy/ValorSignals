package com.valorsky.skysignals.api;

import com.valorsky.skysignals.model.SkyEvent;
import com.valorsky.skysignals.model.SkyEventType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class SkySignalsPlaceholderExpansion {

    private final SkySignalsAPI api;

    public SkySignalsPlaceholderExpansion(SkySignalsAPI api) {
        this.api = api;
    }

    public @NotNull String getIdentifier() {
        return "skysignals";
    }

    public @NotNull String getAuthor() {
        return "ValorSky";
    }

    public @NotNull String getVersion() {
        return "1.0.0";
    }

    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        return switch (identifier) {
            case "active" -> String.valueOf(api.isEventActive());
            case "type" -> {
                SkyEvent event = api.getActiveEvent();
                yield event != null ? event.getType().displayName() : "";
            }
            case "time_remaining" -> {
                SkyEvent event = api.getActiveEvent();
                if (event == null) yield "";
                long seconds = event.getSecondsRemaining();
                long minutes = seconds / 60;
                long secs = seconds % 60;
                yield String.format("%02d:%02d", minutes, secs);
            }
            case "server" -> api.getActiveEvent() != null ? api.getActiveEvent().getServerId() : "";
            default -> null;
        };
    }
}
