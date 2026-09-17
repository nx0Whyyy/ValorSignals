package com.valorsky.skysignals.reward;

import java.util.List;

public record RewardResult(
    boolean success,
    String message,
    List<RewardType> rewardsGiven
) {}
