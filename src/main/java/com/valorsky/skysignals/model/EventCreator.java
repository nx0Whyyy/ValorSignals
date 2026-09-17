package com.valorsky.skysignals.model;

import com.valorsky.skysignals.event.EventContext;

@FunctionalInterface
public interface EventCreator {
    SkyEvent create(EventState state, EventContext context);
}