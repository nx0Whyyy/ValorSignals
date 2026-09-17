package com.valorsky.skysignals.api.event;

import com.valorsky.skysignals.model.EventState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class SkySignalRewardEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final EventState state;
    private final Player player;
    private final Object reward;

    public SkySignalRewardEvent(EventState state, Player player, Object reward) {
        this.state = state;
        this.player = player;
        this.reward = reward;
    }

    public EventState getState() {
        return state;
    }

    public Player getPlayer() {
        return player;
    }

    public Object getReward() {
        return reward;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
