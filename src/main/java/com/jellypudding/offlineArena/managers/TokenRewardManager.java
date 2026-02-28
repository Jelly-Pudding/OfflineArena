package com.jellypudding.offlineArena.managers;

import com.jellypudding.offlineArena.OfflineArena;
import com.jellypudding.offlineArena.zone.DeadZone;
import com.jellypudding.simpleVote.SimpleVote;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TokenRewardManager {

    private final OfflineArena plugin;

    public TokenRewardManager(OfflineArena plugin) {
        this.plugin = plugin;
    }

    /**
     * Awards tokens to every player currently tracked inside the zone.
     * Reward scales linearly from base-reward (full zone) to max-reward (min zone).
     */
    public void rewardPlayers(DeadZone zone) {
        if (zone.getPlayersInZone().isEmpty()) return;

        Plugin sv = Bukkit.getPluginManager().getPlugin("SimpleVote");
        if (!(sv instanceof SimpleVote simpleVote) || !sv.isEnabled()) return;

        // Token amount scales inversely with zone size
        double shrinkRatio = zone.getShrinkRatio();          // 1.0 = full, near 0 = tiny
        int    base        = plugin.getConfigManager().getBaseTokenReward();
        int    max         = plugin.getConfigManager().getMaxTokenReward();
        int    reward      = (int) Math.round(base + (max - base) * (1.0 - shrinkRatio));
        reward = Math.max(base, Math.min(max, reward));

        List<UUID> stale = new ArrayList<>();

        for (UUID uuid : zone.getPlayersInZone()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                stale.add(uuid);
                continue;
            }

            simpleVote.getTokenManager().addTokens(uuid, reward);

            player.sendActionBar(
                Component.text("+" + reward + " tokens ", NamedTextColor.GOLD)
                    .append(Component.text("| Dead Zone  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(zone.getCurrentPhase().getDisplayName(),
                        zone.getCurrentPhase().getTextColor()))
            );
        }

        stale.forEach(zone::removePlayer);
    }

    public void rewardZoneClose(DeadZone zone) {
        int reward = plugin.getConfigManager().getZoneCloseReward();
        if (reward <= 0 || zone.getPlayersInZone().isEmpty()) return;

        Plugin sv = Bukkit.getPluginManager().getPlugin("SimpleVote");
        if (!(sv instanceof SimpleVote simpleVote) || !sv.isEnabled()) return;

        for (UUID uuid : zone.getPlayersInZone()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;

            simpleVote.getTokenManager().addTokens(uuid, reward);
            player.sendActionBar(
                Component.text("+" + reward + " tokens ", NamedTextColor.GOLD)
                    .append(Component.text("| survived the Dead Zone", NamedTextColor.DARK_GRAY))
            );
        }
    }

}
