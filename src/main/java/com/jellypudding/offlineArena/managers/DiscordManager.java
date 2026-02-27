package com.jellypudding.offlineArena.managers;

import com.jellypudding.offlineArena.OfflineArena;

import java.awt.Color;
import java.util.logging.Level;

//Thin wrapper around the DiscordRelay soft-dependency.
public class DiscordManager {

    private final OfflineArena plugin;
    private final boolean available;

    public DiscordManager(OfflineArena plugin) {
        this.plugin = plugin;
        this.available = plugin.getServer().getPluginManager().getPlugin("DiscordRelay") != null;
        if (available) {
            plugin.getLogger().info("DiscordRelay detected. Discord integration active.");
        } else {
            plugin.getLogger().info("DiscordRelay not found. Discord integration disabled.");
        }
    }

    public void sendFormatted(String title, String body, Color color) {
        if (!available || !plugin.getConfigManager().isDiscordEnabled()) return;
        try {
            if (com.jellypudding.discordRelay.DiscordRelayAPI.isReady()) {
                com.jellypudding.discordRelay.DiscordRelayAPI.sendFormattedMessage(title, body, color);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Discord send failed", t);
        }
    }

    public void sendCustom(String message) {
        if (!available || !plugin.getConfigManager().isDiscordEnabled()) return;
        try {
            if (com.jellypudding.discordRelay.DiscordRelayAPI.isReady()) {
                com.jellypudding.discordRelay.DiscordRelayAPI.sendCustomMessage(message);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Discord send failed", t);
        }
    }

    public boolean isAvailable() { return available; }
}
