package com.valorsky.skysignals.api.event;

import com.valorsky.skysignals.model.EventState;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class SkySignalEndEvent extends Event {

    private static final HandlerList handlers = new HandlerList();

    private final EventState state;

    public SkySignalEndEvent(EventState state) {
        this.state = state;
    }

    public EventState getState() {
        return state;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
