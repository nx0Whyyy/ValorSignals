package com.valorsky.skysignals.reward;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.List;

public record ItemReward(Material material, int amount, Component name, List<Component> lore) implements RewardType {}
