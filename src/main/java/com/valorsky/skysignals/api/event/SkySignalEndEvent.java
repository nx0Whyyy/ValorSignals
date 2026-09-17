package com.valorsky.skysignals.api.event;

import com.valorsky.skysignals.model.SkyEvent;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SkySignalEndEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final SkyEvent event;
    private final boolean cancelled;

    public SkySignalEndEvent(SkyEvent event, boolean cancelled) {
        super(true);
        this.event = event;
        this.cancelled = cancelled;
    }

    public SkyEvent getSkyEvent() {
        return event;
    }

    public boolean wasCancelled() {
        return cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}