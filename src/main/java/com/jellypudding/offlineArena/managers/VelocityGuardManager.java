package com.jellypudding.offlineArena.managers;

import com.jellypudding.offlineArena.OfflineArena;
import com.jellypudding.velocityGuard.VelocityGuard;
import com.jellypudding.velocityGuard.api.VelocityGuardAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class VelocityGuardManager {

    private final VelocityGuardAPI api;
    private final boolean available;

    public VelocityGuardManager(OfflineArena plugin) {
        VelocityGuard vg = (VelocityGuard) Bukkit.getPluginManager().getPlugin("VelocityGuard");
        if (vg != null) {
            this.api = vg.getAPI();
            this.available = true;
            plugin.getLogger().info("VelocityGuard detected.");
        } else {
            this.api = null;
            this.available = false;
            plugin.getLogger().warning("VelocityGuard not found.");
        }
    }

    public void enableFlightEnforcement(Player player) {
        if (available) api.enableFlightEnforcement(player);
    }

    public void disableFlightEnforcement(Player player) {
        if (available) api.disableFlightEnforcement(player);
    }

    public boolean isAvailable() { return available; }
}
