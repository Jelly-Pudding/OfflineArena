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
    public int getShrinkIntervalAwakeningMin()    { return config.getInt("zone.shrink-interval-awakening-min",    20); }
    public int getShrinkIntervalAwakeningMax()    { return config.getInt("zone.shrink-interval-awakening-max",    30); }
    public int getShrinkIntervalIntensifyingMin() { return config.getInt("zone.shrink-interval-intensifying-min", 17); }
    public int getShrinkIntervalIntensifyingMax() { return config.getInt("zone.shrink-interval-intensifying-max", 23); }
    public int getShrinkIntervalCriticalMin()     { return config.getInt("zone.shrink-interval-critical-min",     13); }
    public int getShrinkIntervalCriticalMax()     { return config.getInt("zone.shrink-interval-critical-max",     18); }
    public int getShrinkIntervalCollapseMin()     { return config.getInt("zone.shrink-interval-collapse-min",      8); }
    public int getShrinkIntervalCollapseMax()     { return config.getInt("zone.shrink-interval-collapse-max",     15); }
    public double getShrinkAmountMin()      { return config.getDouble("zone.shrink-amount-min", 2.0); }
    public double getShrinkAmountMax()      { return config.getDouble("zone.shrink-amount-max", 5.0); }
    public int    getRespawnDelayMin()      { return config.getInt("zone.respawn-delay-min", 600); }
    public int    getRespawnDelayMax()      { return config.getInt("zone.respawn-delay-max", 1800); }
    public int    getCollapseDelayMin()     { return config.getInt("zone.collapse-delay-min", 20); }
    public int    getCollapseDelayMax()     { return config.getInt("zone.collapse-delay-max", 45); }
    public boolean isAutoStart()            { return config.getBoolean("zone.auto-start", true); }
    public double getZoneHeightMin()        { return config.getDouble("zone.height-min", 20.0); }
    public double getZoneHeightMax()        { return config.getDouble("zone.height-max", 200.0); }

    // Tokens
    public int getBaseTokenReward()      { return config.getInt("tokens.base-reward",        1); }
    public int getMaxTokenReward()       { return config.getInt("tokens.max-reward",          2); }
    public int getTokenIntervalMin()     { return config.getInt("tokens.reward-interval-min", 120); }
    public int getTokenIntervalMax()     { return config.getInt("tokens.reward-interval-max", 300); }
    public int getZoneCloseReward()      { return config.getInt("tokens.zone-close-reward",   3); }

    // Mobs
    public int    getBaseSpawnCountMin()              { return config.getInt("mobs.base-spawn-count-min",    8); }
    public int    getBaseSpawnCountMax()              { return config.getInt("mobs.base-spawn-count-max",   12); }
    public double getPhaseMultiplierAwakening()       { return config.getDouble("mobs.phase-multiplier-awakening",    7.0); }
    public double getPhaseMultiplierIntensifying()    { return config.getDouble("mobs.phase-multiplier-intensifying", 2.5); }
    public double getPhaseMultiplierCritical()        { return config.getDouble("mobs.phase-multiplier-critical",     1.5); }
    public double getPhaseMultiplierCollapse()        { return config.getDouble("mobs.phase-multiplier-collapse",     1.0); }
    public int getPlayerCapacity()      { return config.getInt("mobs.player-capacity", 10); }
    public int getMaxTotalMobsMin()     { return config.getInt("mobs.max-total-min", 160); }
    public int getMaxTotalMobsMax()     { return config.getInt("mobs.max-total-max", 240); }
    public int getSpawnIntervalMin()    { return config.getInt("mobs.spawn-interval-min", 8); }
    public int getSpawnIntervalMax()    { return config.getInt("mobs.spawn-interval-max", 15); }
    public int    getMaxWithers()         { return config.getInt("mobs.max-withers", 3); }
    public double getGhostSpawnChance()   { return config.getDouble("mobs.ghost-spawn-chance", 0.05); }

    // Environment
    public int getFireIntervalMin()               { return config.getInt("environment.fire-interval-min", 200); }
    public int getFireIntervalMax()               { return config.getInt("environment.fire-interval-max", 400); }
    public int getFireAwakeningMin()              { return config.getInt("environment.fire-awakening-min", 1); }
    public int getFireAwakeningMax()              { return config.getInt("environment.fire-awakening-max", 3); }
    public int getFireIntensifyingMin()           { return config.getInt("environment.fire-intensifying-min", 3); }
    public int getFireIntensifyingMax()           { return config.getInt("environment.fire-intensifying-max", 7); }
    public int getFireCriticalMin()               { return config.getInt("environment.fire-critical-min", 7); }
    public int getFireCriticalMax()               { return config.getInt("environment.fire-critical-max", 12); }
    public int getFireCollapseMin()               { return config.getInt("environment.fire-collapse-min", 10); }
    public int getFireCollapseMax()               { return config.getInt("environment.fire-collapse-max", 18); }
    public int   getExplosionsAwakeningMin()      { return config.getInt("environment.explosions-awakening-min",     0); }
    public int   getExplosionsAwakeningMax()      { return config.getInt("environment.explosions-awakening-max",     1); }
    public int   getExplosionsIntensifyingMin()   { return config.getInt("environment.explosions-intensifying-min",  0); }
    public int   getExplosionsIntensifyingMax()   { return config.getInt("environment.explosions-intensifying-max",  2); }
    public int   getExplosionsCriticalMin()       { return config.getInt("environment.explosions-critical-min",      1); }
    public int   getExplosionsCriticalMax()       { return config.getInt("environment.explosions-critical-max",      3); }
    public int   getExplosionsCollapseMin()       { return config.getInt("environment.explosions-collapse-min",      1); }
    public int   getExplosionsCollapseMax()       { return config.getInt("environment.explosions-collapse-max",      3); }
    public double getExplosionPowerMin()          { return config.getDouble("environment.explosion-power-min",       1.5); }
    public double getExplosionPowerMax()          { return config.getDouble("environment.explosion-power-max",       3.0); }
    public int   getFinaleExplosionsMin()         { return config.getInt("environment.finale-explosions-min",        6); }
    public int   getFinaleExplosionsMax()         { return config.getInt("environment.finale-explosions-max",        14); }
    public double getFinaleExplosionPowerMin()    { return config.getDouble("environment.finale-explosion-power-min", 3.0); }
    public double getFinaleExplosionPowerMax()    { return config.getDouble("environment.finale-explosion-power-max", 6.0); }
    public double getLightningChanceCritical()    { return config.getDouble("environment.lightning-chance-critical", 0.3); }
    public double getLightningChanceCollapse()    { return config.getDouble("environment.lightning-chance-collapse", 0.6); }

    // Discord
    public boolean isDiscordEnabled()       { return config.getBoolean("discord.enabled", true); }
    public boolean isDiscordAnnounceOpen()  { return config.getBoolean("discord.announce-open", true); }
    public boolean isDiscordAnnounceClose() { return config.getBoolean("discord.announce-close", true); }
}
