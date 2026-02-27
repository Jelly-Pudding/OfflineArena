package com.jellypudding.offlineArena.managers;

import com.jellypudding.offlineArena.OfflineArena;
import com.jellypudding.offlineArena.zone.DeadZone;
import com.jellypudding.offlineArena.zone.ZonePhase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.awt.Color;
import java.time.Duration;
import java.util.Random;

public class ZoneManager {

    private final OfflineArena plugin;
    private DeadZone activeZone;
    private BossBar  zoneBossBar;

    private BukkitTask shrinkTask;
    private BukkitTask mobTask;
    private BukkitTask tokenTask;
    private BukkitTask particleTask;
    private BukkitTask respawnTask;

    // Per-zone randomised parameters (set fresh on each openZone())
    private double activeShrinkAmount;
    private int    activeShrinkInterval;
    private int    activeSpawnInterval;

    private final Random random = new Random();

    public ZoneManager(OfflineArena plugin) {
        this.plugin = plugin;
    }

    public void scheduleInitialZone() {
        // 10 seconds after enable
        Bukkit.getScheduler().runTaskLater(plugin, this::openZone, 200L);
    }

    public void openZone() {
        if (activeZone != null) return;

        // Always use the primary/default world
        if (Bukkit.getWorlds().isEmpty()) {
            plugin.getLogger().warning("No worlds loaded: cannot open Dead Zone.");
            return;
        }
        World world = Bukkit.getWorlds().get(0);

        // Randomise spawn location
        double spawnRadius = plugin.getConfigManager().getSpawnRadius();
        double originX     = plugin.getConfigManager().getOriginX();
        double originZ     = plugin.getConfigManager().getOriginZ();

        double angle = random.nextDouble() * 2 * Math.PI;
        double dist  = random.nextDouble() * spawnRadius;
        double cx    = originX + dist * Math.cos(angle);
        double cz    = originZ + dist * Math.sin(angle);

        // Pick randomised zone parameters.
        double rMin = plugin.getConfigManager().getInitialRadiusMin();
        double rMax = plugin.getConfigManager().getInitialRadiusMax();
        double initialRadius = rMin + random.nextDouble() * (rMax - rMin);

        int siMin = plugin.getConfigManager().getShrinkIntervalMin();
        int siMax = plugin.getConfigManager().getShrinkIntervalMax();
        activeShrinkInterval = siMin + random.nextInt(Math.max(1, siMax - siMin + 1));

        double saMin = plugin.getConfigManager().getShrinkAmountMin();
        double saMax = plugin.getConfigManager().getShrinkAmountMax();
        activeShrinkAmount = saMin + random.nextDouble() * (saMax - saMin);

        int spMin = plugin.getConfigManager().getSpawnIntervalMin();
        int spMax = plugin.getConfigManager().getSpawnIntervalMax();
        activeSpawnInterval = spMin + random.nextInt(Math.max(1, spMax - spMin + 1));

        activeZone = new DeadZone(new Location(world, cx, 64, cz), initialRadius);

        // Seed players already standing inside the zone.
        for (Player p : world.getPlayers()) {
            if (activeZone.isInside(p.getLocation())) {
                activeZone.addPlayer(p.getUniqueId());
            }
        }

        createBossBar();
        broadcastZoneOpen();
        startTasks();

        plugin.getLogger().info(String.format(
            "Dead Zone opened at (%.0f, %.0f) radius=%.0f shrinkInterval=%ds shrinkAmount=%.1f spawnInterval=%ds",
            cx, cz, initialRadius, activeShrinkInterval, activeShrinkAmount, activeSpawnInterval));
    }

    public void closeZone(boolean natural) {
        if (activeZone == null) return;

        plugin.getMobSpawnManager().clearZoneMobs(activeZone);
        broadcastZoneClose();
        cancelTasks();
        destroyBossBar();
        activeZone = null;

        if (natural) scheduleRespawn();
    }

    private void scheduleRespawn() {
        int min   = plugin.getConfigManager().getRespawnDelayMin();
        int max   = plugin.getConfigManager().getRespawnDelayMax();
        int delay = min + random.nextInt(Math.max(1, max - min + 1));

        respawnTask = Bukkit.getScheduler().runTaskLater(plugin, this::openZone, (long) delay * 20L);
        plugin.getLogger().info("Next Dead Zone in " + delay + "s.");
    }

    private void startTasks() {
        long shrinkTicks  = (long) activeShrinkInterval * 20L;
        long mobTicks     = (long) activeSpawnInterval * 20L;
        long tokenTicks   = (long) plugin.getConfigManager().getTokenRewardInterval() * 20L;

        shrinkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickShrink, shrinkTicks, shrinkTicks);
        mobTask    = Bukkit.getScheduler().runTaskTimer(plugin, this::tickMobs,   mobTicks,    mobTicks);
        tokenTask  = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTokens, tokenTicks,  tokenTicks);
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickParticles, 40L, 60L); // every 3 s
    }

    private void tickShrink() {
        if (activeZone == null) return;

        double shrinkAmount = activeShrinkAmount;
        double minRadius    = plugin.getConfigManager().getMinRadius();
        boolean phaseChanged = activeZone.shrink(shrinkAmount);

        if (activeZone.getCurrentRadius() <= minRadius) {
            closeZone(true);
            return;
        }

        if (phaseChanged) onPhaseChange(activeZone.getCurrentPhase());
        updateBossBar();
    }

    private void tickMobs() {
        if (activeZone != null) plugin.getMobSpawnManager().spawnMobs(activeZone);
    }

    private void tickTokens() {
        if (activeZone != null) plugin.getTokenRewardManager().rewardPlayers(activeZone);
    }

    private void tickParticles() {
        if (activeZone != null) plugin.getParticleManager().drawZoneBorder(activeZone);
    }

    private void cancelTasks() {
        if (shrinkTask   != null) { shrinkTask.cancel();   shrinkTask   = null; }
        if (mobTask      != null) { mobTask.cancel();      mobTask      = null; }
        if (tokenTask    != null) { tokenTask.cancel();    tokenTask    = null; }
        if (particleTask != null) { particleTask.cancel(); particleTask = null; }
    }

    private void onPhaseChange(ZonePhase phase) {
        for (java.util.UUID uuid : activeZone.getPlayersInZone()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.showTitle(Title.title(
                Component.text(phase.getDisplayName(), phase.getTextColor()).decorate(TextDecoration.BOLD),
                Component.text("The zone is shrinking.", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))
            ));
            float pitch = phase == ZonePhase.COLLAPSE ? 0.5f : 1.0f;
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.9f, pitch);
        }
    }

    private void createBossBar() {
        zoneBossBar = Bukkit.createBossBar(
            buildBossBarTitle(),
            org.bukkit.boss.BarColor.GREEN,
            BarStyle.SOLID
        );
        zoneBossBar.setProgress(1.0);
        zoneBossBar.setVisible(true);
        // Players are added individually as they enter the zone
    }

    private void updateBossBar() {
        if (zoneBossBar == null || activeZone == null) return;
        zoneBossBar.setTitle(buildBossBarTitle());
        zoneBossBar.setColor(activeZone.getCurrentPhase().getBarColor());
        double progress = Math.max(0.0, Math.min(1.0, activeZone.getShrinkRatio()));
        zoneBossBar.setProgress(progress);
    }

    private String buildBossBarTitle() {
        if (activeZone == null) return "Dead Zone";
        ZonePhase p = activeZone.getCurrentPhase();
        return p.getColorCode() + "Dead Zone §8| " + p.getColorCode() + p.name();
    }

    private void destroyBossBar() {
        if (zoneBossBar != null) {
            zoneBossBar.removeAll();
            zoneBossBar.setVisible(false);
            zoneBossBar = null;
        }
    }

    public void addPlayerToBossBar(Player player) {
        if (zoneBossBar != null) zoneBossBar.addPlayer(player);
    }

    public void removePlayerFromBossBar(Player player) {
        if (zoneBossBar != null) zoneBossBar.removePlayer(player);
    }

    private void broadcastZoneOpen() {
        Location c     = activeZone.getCenter();
        String   coord = "(" + String.format("%.0f", c.getX()) + ", " + String.format("%.0f", c.getZ()) + ")";

        Bukkit.getServer().broadcast(
            Component.text("A Dead Zone has opened at " + coord + ".", NamedTextColor.RED)
        );

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(
                Component.text("Dead Zone", NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD),
                Component.text(coord, NamedTextColor.RED),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(800))
            ));
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.4f, 0.7f);
        }

        if (plugin.getConfigManager().isDiscordAnnounceOpen()) {
            plugin.getDiscordManager().sendFormatted(
                "Dead Zone",
                "A Dead Zone has opened at " + coord + ".",
                Color.RED
            );
        }
    }

    private void broadcastZoneClose() {
        Bukkit.getServer().broadcast(
            Component.text("The Dead Zone has collapsed.", NamedTextColor.GRAY)
        );

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.3f, 0.4f);
        }

        if (plugin.getConfigManager().isDiscordAnnounceClose()) {
            plugin.getDiscordManager().sendFormatted(
                "Dead Zone",
                "The Dead Zone has collapsed.",
                Color.DARK_GRAY
            );
        }
    }

    public void shutdown() {
        cancelTasks();
        if (respawnTask != null) { respawnTask.cancel(); respawnTask = null; }
        if (activeZone  != null) {
            plugin.getMobSpawnManager().clearZoneMobs(activeZone);
            activeZone = null;
        }
        destroyBossBar();
    }

    public DeadZone getActiveZone()  { return activeZone; }
    public boolean  hasActiveZone()  { return activeZone != null; }
    public BossBar  getZoneBossBar() { return zoneBossBar; }
}
