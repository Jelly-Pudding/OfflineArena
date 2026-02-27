package com.jellypudding.offlineArena;

import com.jellypudding.offlineArena.commands.OfflineArenaCommand;
import com.jellypudding.offlineArena.config.ConfigManager;
import com.jellypudding.offlineArena.listeners.MobDeathListener;
import com.jellypudding.offlineArena.listeners.PlayerZoneListener;
import com.jellypudding.offlineArena.managers.DiscordManager;
import com.jellypudding.offlineArena.managers.MobSpawnManager;
import com.jellypudding.offlineArena.managers.ParticleManager;
import com.jellypudding.offlineArena.managers.TokenRewardManager;
import com.jellypudding.offlineArena.managers.VelocityGuardManager;
import com.jellypudding.offlineArena.managers.ZoneManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class OfflineArena extends JavaPlugin {

    private static OfflineArena instance;

    private ConfigManager        configManager;
    private DiscordManager       discordManager;
    private VelocityGuardManager velocityGuardManager;
    private MobSpawnManager      mobSpawnManager;
    private TokenRewardManager   tokenRewardManager;
    private ParticleManager      particleManager;
    private ZoneManager          zoneManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        configManager        = new ConfigManager(this);
        discordManager       = new DiscordManager(this);
        velocityGuardManager = new VelocityGuardManager(this);
        mobSpawnManager      = new MobSpawnManager(this);
        tokenRewardManager = new TokenRewardManager(this);
        particleManager    = new ParticleManager();
        zoneManager        = new ZoneManager(this);

        getServer().getPluginManager().registerEvents(new PlayerZoneListener(this), this);
        getServer().getPluginManager().registerEvents(new MobDeathListener(this), this);

        OfflineArenaCommand cmd = new OfflineArenaCommand(this);
        getCommand("offlinearena").setExecutor(cmd);
        getCommand("offlinearena").setTabCompleter(cmd);

        if (configManager.isAutoStart()) {
            zoneManager.scheduleInitialZone();
        }

        getLogger().info("OfflineArena enabled.");
    }

    @Override
    public void onDisable() {
        if (zoneManager != null) zoneManager.shutdown();
        getLogger().info("OfflineArena disabled.");
    }

    public static OfflineArena getInstance()           { return instance; }
    public ConfigManager        getConfigManager()          { return configManager; }
    public DiscordManager       getDiscordManager()         { return discordManager; }
    public VelocityGuardManager getVelocityGuardManager()  { return velocityGuardManager; }
    public MobSpawnManager      getMobSpawnManager()        { return mobSpawnManager; }
    public TokenRewardManager   getTokenRewardManager()     { return tokenRewardManager; }
    public ParticleManager      getParticleManager()        { return particleManager; }
    public ZoneManager          getZoneManager()            { return zoneManager; }
}
