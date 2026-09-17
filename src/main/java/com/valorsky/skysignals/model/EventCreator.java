package com.valorsky.skysignals.model;

@FunctionalInterface
public interface EventCreator {
    SkyEvent create(EventState state);
}
