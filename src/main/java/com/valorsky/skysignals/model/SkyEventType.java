package com.valorsky.skysignals.model;

import java.util.Map;

public enum SkyEventType {
    METEOR("☄", "Météorite", "meteor"),
    STORM("🌩", "Orage", "storm"),
    SKY_CHEST("🎁", "Caisse Céleste", "sky_chest"),
    MOB_INVASION("👾", "Invasion Céleste", "mob_invasion"),
    MINERAL_RAIN("💎", "Pluie de Minerais", "mineral_rain"),
    GROWTH_BOOST("🌱", "Croissance Céleste", "growth_boost");

    private final String symbol;
    private final String displayName;
    private final String configKey;

    SkyEventType(String symbol, String displayName, String configKey) {
        this.symbol = symbol;
        this.displayName = displayName;
        this.configKey = configKey;
    }

    public String symbol() {
        return symbol;
    }

    public String displayName() {
        return displayName;
    }

    public String configKey() {
        return configKey;
    }

    public static SkyEventType fromId(String id) {
        if (id == null) return null;
        for (SkyEventType type : values()) {
            if (type.name().equalsIgnoreCase(id)) {
                return type;
            }
        }
        String normalized = id.replace("-", "_");
        for (SkyEventType type : values()) {
            if (type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return null;
    }
}