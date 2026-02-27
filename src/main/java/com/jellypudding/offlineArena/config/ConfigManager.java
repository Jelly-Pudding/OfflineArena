package com.jellypudding.offlineArena.config;

import com.jellypudding.offlineArena.OfflineArena;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final OfflineArena plugin;
    private FileConfiguration config;

    public ConfigManager(OfflineArena plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    // Zone
    public double getOriginX()              { return config.getDouble("zone.origin.x", 0); }
    public double getOriginZ()              { return config.getDouble("zone.origin.z", 0); }
    public double getSpawnRadius()          { return config.getDouble("zone.spawn-radius", 500); }
    public double getInitialRadiusMin()     { return config.getDouble("zone.initial-radius-min", 100.0); }
    public double getInitialRadiusMax()     { return config.getDouble("zone.initial-radius-max", 200.0); }
    public double getMinRadius()            { return config.getDouble("zone.min-radius", 15.0); }
    public int    getShrinkIntervalMin()    { return config.getInt("zone.shrink-interval-min", 20); }
    public int    getShrinkIntervalMax()    { return config.getInt("zone.shrink-interval-max", 45); }
    public double getShrinkAmountMin()      { return config.getDouble("zone.shrink-amount-min", 2.0); }
    public double getShrinkAmountMax()      { return config.getDouble("zone.shrink-amount-max", 5.0); }
    public int    getRespawnDelayMin()      { return config.getInt("zone.respawn-delay-min", 600); }
    public int    getRespawnDelayMax()      { return config.getInt("zone.respawn-delay-max", 1800); }
    public int    getCollapseDelayMin()     { return config.getInt("zone.collapse-delay-min", 20); }
    public int    getCollapseDelayMax()     { return config.getInt("zone.collapse-delay-max", 45); }
    public boolean isAutoStart()            { return config.getBoolean("zone.auto-start", true); }

    // Tokens
    public int getBaseTokenReward()      { return config.getInt("tokens.base-reward", 1); }
    public int getMaxTokenReward()       { return config.getInt("tokens.max-reward", 3); }
    public int getTokenRewardInterval()  { return config.getInt("tokens.reward-interval", 60); }

    // Mobs
    public int getBaseSpawnCount()      { return config.getInt("mobs.base-spawn-count", 5); }
    public int getPlayerCapacity()      { return config.getInt("mobs.player-capacity", 10); }
    public int getMaxTotalMobs()        { return config.getInt("mobs.max-total", 30); }
    public int getSpawnIntervalMin()    { return config.getInt("mobs.spawn-interval-min", 8); }
    public int getSpawnIntervalMax()    { return config.getInt("mobs.spawn-interval-max", 15); }
    public int getMaxWithers()          { return config.getInt("mobs.max-withers", 3); }

    // Discord
    public boolean isDiscordEnabled()       { return config.getBoolean("discord.enabled", true); }
    public boolean isDiscordAnnounceOpen()  { return config.getBoolean("discord.announce-open", true); }
    public boolean isDiscordAnnounceClose() { return config.getBoolean("discord.announce-close", true); }
}
