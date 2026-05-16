package com.jellypudding.offlineArena.zone;

import net.kyori.adventure.text.format.NamedTextColor;

public enum ZonePhase {

    AWAKENING    ("Awakening",    NamedTextColor.GREEN),
    INTENSIFYING ("Intensifying", NamedTextColor.YELLOW),
    CRITICAL     ("Critical",     NamedTextColor.GOLD),
    COLLAPSE     ("Collapse",     NamedTextColor.RED);

    private final String displayName;
    private final NamedTextColor textColor;

    ZonePhase(String displayName, NamedTextColor textColor) {
        this.displayName = displayName;
        this.textColor   = textColor;
    }

    public static ZonePhase fromRatio(double ratio) {
        if (ratio > 0.65) return AWAKENING;
        if (ratio > 0.35) return INTENSIFYING;
        if (ratio > 0.20) return CRITICAL;
        return COLLAPSE;
    }

    public String getDisplayName()       { return displayName; }
    public NamedTextColor getTextColor() { return textColor; }
}
