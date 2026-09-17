package com.valorsky.skysignals.api.event;

import com.valorsky.skysignals.model.SkyEvent;
import com.valorsky.skysignals.model.SkyEventPhase;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class SkySignalPhaseChangeEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final SkyEvent event;
    private final SkyEventPhase oldPhase;
    private final SkyEventPhase newPhase;

    public SkySignalPhaseChangeEvent(SkyEvent event, SkyEventPhase oldPhase, SkyEventPhase newPhase) {
        super(false);
        this.event = event;
        this.oldPhase = oldPhase;
        this.newPhase = newPhase;
    }

    public SkyEvent getSkyEvent() {
        return event;
    }

    public SkyEventPhase getOldPhase() {
        return oldPhase;
    }

    public SkyEventPhase getNewPhase() {
        return newPhase;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}