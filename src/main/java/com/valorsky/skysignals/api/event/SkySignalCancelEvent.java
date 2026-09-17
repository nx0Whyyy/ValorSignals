package com.valorsky.skysignals.api.event;

import com.valorsky.skysignals.model.SkyEvent;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SkySignalCancelEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final SkyEvent event;

    public SkySignalCancelEvent(SkyEvent event) {
        super(true);
        this.event = event;
    }

    public SkyEvent getSkyEvent() {
        return event;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}