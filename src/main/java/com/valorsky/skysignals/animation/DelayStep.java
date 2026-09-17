package com.valorsky.skysignals.animation;

public record DelayStep(
    int ticks
) implements AnimationStep {

    @Override
    public void execute(AnimationContext context) {
        // Delay is handled by AnimationService scheduling
    }
}