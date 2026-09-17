package com.valorsky.skysignals.reward;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.List;
import java.util.Map;

record CommandReward(String command) implements RewardType {}

record MoneyReward(double amount) implements RewardType {}

record XpReward(int amount) implements RewardType {}

record CustomReward(String type, Map<String, Object> data) implements RewardType {}

record RewardContext(
    java.util.UUID eventId,
    com.valorsky.skysignals.model.SkyEventType eventType,
    java.util.UUID playerId,
    String serverId,
    long timestamp,
    Map<String, String> placeholders
) {}

sealed interface RewardType permits ItemReward, CommandReward, MoneyReward, XpReward, CustomReward {}