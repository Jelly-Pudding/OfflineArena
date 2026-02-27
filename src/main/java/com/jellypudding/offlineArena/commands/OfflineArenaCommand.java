package com.jellypudding.offlineArena.commands;

import com.jellypudding.offlineArena.OfflineArena;
import com.jellypudding.offlineArena.zone.DeadZone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class OfflineArenaCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("reload", "status", "open", "close");

    private final OfflineArena plugin;

    public OfflineArenaCommand(OfflineArena plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload" -> {
                plugin.getConfigManager().reload();
                sender.sendMessage(prefix(NamedTextColor.GREEN)
                    .append(Component.text("Configuration reloaded.", NamedTextColor.GREEN)));
            }

            case "status" -> {
                if (!plugin.getZoneManager().hasActiveZone()) {
                    sender.sendMessage(prefix(NamedTextColor.GRAY)
                        .append(Component.text("No Dead Zone is currently active.", NamedTextColor.GRAY)));
                    return true;
                }
                DeadZone zone = plugin.getZoneManager().getActiveZone();
                Location  c   = zone.getCenter();
                sender.sendMessage(prefix(NamedTextColor.RED)
                    .append(Component.text("Dead Zone is ACTIVE", NamedTextColor.RED)));
                info(sender, "Location",
                    "(" + fmt(c.getX()) + ", " + fmt(c.getZ()) + ")  world: " + c.getWorld().getName());
                info(sender, "Radius",
                    fmt(zone.getCurrentRadius()) + " / " + fmt(zone.getInitialRadius()) + " blocks");
                info(sender, "Phase",    zone.getCurrentPhase().getDisplayName());
                info(sender, "Shrink ratio", String.format("%.1f%%", zone.getShrinkRatio() * 100));
                info(sender, "Players inside", String.valueOf(zone.getPlayersInZone().size()));
                info(sender, "Zone mobs",      String.valueOf(zone.getMobCount()));
            }

            case "open" -> {
                if (plugin.getZoneManager().hasActiveZone()) {
                    sender.sendMessage(prefix(NamedTextColor.RED)
                        .append(Component.text("A Dead Zone is already active!", NamedTextColor.RED)));
                } else {
                    plugin.getZoneManager().openZone();
                    sender.sendMessage(prefix(NamedTextColor.GREEN)
                        .append(Component.text("Opening a new Dead Zone…", NamedTextColor.GREEN)));
                }
            }

            case "close" -> {
                if (!plugin.getZoneManager().hasActiveZone()) {
                    sender.sendMessage(prefix(NamedTextColor.RED)
                        .append(Component.text("No Dead Zone is active!", NamedTextColor.RED)));
                } else {
                    plugin.getZoneManager().closeZone(false);
                    sender.sendMessage(prefix(NamedTextColor.GREEN)
                        .append(Component.text("Dead Zone forcefully closed.", NamedTextColor.GREEN)));
                }
            }

            default -> sendHelp(sender);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .toList();
        }
        return Collections.emptyList();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("[OfflineArena]", NamedTextColor.GOLD)
            .append(Component.text(" Commands:", NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("  /offlinearena reload", NamedTextColor.AQUA)
            .append(Component.text(" — reload config", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /offlinearena status", NamedTextColor.AQUA)
            .append(Component.text(" — show current zone info", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /offlinearena open",   NamedTextColor.AQUA)
            .append(Component.text(" — force-open a zone",      NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /offlinearena close",  NamedTextColor.AQUA)
            .append(Component.text(" — force-close the zone",   NamedTextColor.GRAY)));
    }

    private Component prefix(NamedTextColor accent) {
        return Component.text("[OfflineArena] ", accent);
    }

    private void info(CommandSender sender, String key, String value) {
        sender.sendMessage(
            Component.text("  " + key + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE))
        );
    }

    private String fmt(double v) { return String.format("%.0f", v); }
}
