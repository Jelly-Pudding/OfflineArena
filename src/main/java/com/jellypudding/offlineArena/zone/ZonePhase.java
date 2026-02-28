package com.jellypudding.offlineArena.zone;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.boss.BarColor;

public enum ZonePhase {

    AWAKENING    ("§a§lAWAKENING",    "§a", NamedTextColor.GREEN,  BarColor.GREEN),
    INTENSIFYING ("§e§lINTENSIFYING", "§e", NamedTextColor.YELLOW, BarColor.YELLOW),
    CRITICAL     ("§6§lCRITICAL",     "§6", NamedTextColor.GOLD,   BarColor.RED),
    COLLAPSE     ("§c§lCOLLAPSE",     "§c", NamedTextColor.RED,    BarColor.RED);

    private final String displayName;
    private final String colorCode;
    private final NamedTextColor textColor;
    private final BarColor barColor;

    ZonePhase(String displayName, String colorCode, NamedTextColor textColor, BarColor barColor) {
        this.displayName = displayName;
        this.colorCode   = colorCode;
        this.textColor   = textColor;
        this.barColor    = barColor;
    }

    public static ZonePhase fromRatio(double ratio) {
        if (ratio > 0.65) return AWAKENING;
        if (ratio > 0.35) return INTENSIFYING;
        if (ratio > 0.20) return CRITICAL;
        return COLLAPSE;
    }

    public String getDisplayName()       { return displayName; }
    public String getColorCode()         { return colorCode; }
    public NamedTextColor getTextColor() { return textColor; }
    public BarColor getBarColor()        { return barColor; }
}
