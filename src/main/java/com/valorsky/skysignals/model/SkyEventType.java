package com.valorsky.skysignals.model;

import java.util.Map;

public enum SkyEventType {
    METEOR("☄", "Météorite"),
    STORM("🌩", "Orage"),
    SKY_CHEST("🎁", "Caisse Céleste"),
    MOB_INVASION("👾", "Invasion"),
    MINERAL_RAIN("💎", "Pluie de Minerais"),
    GROWTH_BOOST("🌱", "Croissance Céleste");

    private final String symbol;
    private final String displayName;

    SkyEventType(String symbol, String displayName) {
        this.symbol = symbol;
        this.displayName = displayName;
    }

    public String symbol() {
        return symbol;
    }

    public String displayName() {
        return displayName;
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

    public static Map<String, String> getAllowedEvents(com.valorsky.skysignals.config.Config config) {
        return null;
    }
}
