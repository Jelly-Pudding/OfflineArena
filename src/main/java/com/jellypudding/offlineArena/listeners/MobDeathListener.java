package com.jellypudding.offlineArena.listeners;

import com.jellypudding.offlineArena.OfflineArena;
import com.jellypudding.offlineArena.zone.DeadZone;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class MobDeathListener implements Listener {

    private final OfflineArena plugin;

    public MobDeathListener(OfflineArena plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!plugin.getMobSpawnManager().isZoneMob(event.getEntity())) return;

        DeadZone zone = plugin.getZoneManager().getActiveZone();
        if (zone == null) return;

        zone.untrackMob(event.getEntity().getUniqueId());
    }
}
