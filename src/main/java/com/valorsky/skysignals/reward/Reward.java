package com.valorsky.skysignals.reward;

import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Reward {

    private final int moneyMin;
    private final int moneyMax;
    private final List<String> commands;
    private final List<ItemReward> items;

    public Reward(int moneyMin, int moneyMax, List<String> commands, List<ItemReward> items) {
        this.moneyMin = moneyMin;
        this.moneyMax = moneyMax;
        this.commands = commands;
        this.items = items;
    }

    public int getMoneyMin() {
        return moneyMin;
    }

    public int getMoneyMax() {
        return moneyMax;
    }

    public List<String> getCommands() {
        return commands;
    }

    public List<ItemReward> getItems() {
        return items;
    }

    public static final class ItemReward {
        private final Material material;
        private final int amount;
        private final String name;
        private final List<String> lore;

        public ItemReward(Material material, int amount, String name, List<String> lore) {
            this.material = material;
            this.amount = amount;
            this.name = name;
            this.lore = lore;
        }

        public Material getMaterial() {
            return material;
        }

        public int getAmount() {
            return amount;
        }

        public String getName() {
            return name;
        }

        public List<String> getLore() {
            return lore;
        }
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("moneyMin", moneyMin);
        map.put("moneyMax", moneyMax);
        map.put("commands", commands);
        return map;
    }
}
