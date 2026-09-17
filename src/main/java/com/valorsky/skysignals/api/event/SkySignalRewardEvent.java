package com.valorsky.skysignals.api.event;

import com.valorsky.skysignals.model.SkyEvent;
import com.valorsky.skysignals.reward.RewardResult;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public class SkySignalRewardEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final SkyEvent event;
    private final UUID playerId;
    private final RewardResult result;

    public SkySignalRewardEvent(SkyEvent event, UUID playerId, RewardResult result) {
        super(true);
        this.event = event;
        this.playerId = playerId;
        this.result = result;
    }

    public SkyEvent getSkyEvent() {
        return event;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public RewardResult getResult() {
        return result;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}