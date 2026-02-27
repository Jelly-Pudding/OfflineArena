package com.jellypudding.offlineArena.listeners;

import com.jellypudding.offlineArena.OfflineArena;
import com.jellypudding.offlineArena.zone.DeadZone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class PlayerZoneListener implements Listener {

    private final OfflineArena plugin;

    public PlayerZoneListener(OfflineArena plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Skip if only head-rotation changed (no block movement)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()) return;

        checkZoneTransition(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        checkZoneTransition(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        DeadZone zone = plugin.getZoneManager().getActiveZone();
        if (zone != null && zone.isInside(event.getPlayer().getLocation())) {
            zone.addPlayer(event.getPlayer().getUniqueId());
            notifyEnter(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        DeadZone zone = plugin.getZoneManager().getActiveZone();
        if (zone != null) {
            zone.removePlayer(event.getPlayer().getUniqueId());
            plugin.getZoneManager().removePlayerFromBossBar(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        DeadZone zone = plugin.getZoneManager().getActiveZone();
        if (zone == null) return;
        // Respawn happens before the player actually moves there, so re-check after 1 tick
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            boolean inside = zone.isInside(event.getPlayer().getLocation());
            boolean was    = zone.getPlayersInZone().contains(event.getPlayer().getUniqueId());
            if (inside && !was) {
                zone.addPlayer(event.getPlayer().getUniqueId());
                notifyEnter(event.getPlayer());
            } else if (!inside && was) {
                zone.removePlayer(event.getPlayer().getUniqueId());
                notifyLeave(event.getPlayer());
            }
        }, 1L);
    }

    private void checkZoneTransition(Player player, org.bukkit.Location to) {
        if (to == null) return;
        DeadZone zone = plugin.getZoneManager().getActiveZone();
        if (zone == null) return;

        boolean wasInside = zone.getPlayersInZone().contains(player.getUniqueId());
        boolean isInside  = zone.isInside(to);

        if (!wasInside && isInside) {
            zone.addPlayer(player.getUniqueId());
            notifyEnter(player);
        } else if (wasInside && !isInside) {
            zone.removePlayer(player.getUniqueId());
            notifyLeave(player);
        }
    }

    private void notifyEnter(Player player) {
        player.sendMessage(Component.text("You have entered the Dead Zone.", NamedTextColor.RED));
        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 0.7f, 0.4f);
        plugin.getZoneManager().addPlayerToBossBar(player);
    }

    private void notifyLeave(Player player) {
        player.sendMessage(Component.text("You have left the Dead Zone.", NamedTextColor.GRAY));
        plugin.getZoneManager().removePlayerFromBossBar(player);
    }
}
