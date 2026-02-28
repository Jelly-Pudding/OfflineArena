package com.jellypudding.offlineArena.managers;

import com.jellypudding.offlineArena.OfflineArena;
import com.jellypudding.offlineArena.zone.DeadZone;
import com.jellypudding.offlineArena.zone.ZonePhase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
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
    private BukkitTask fireTask;
    private BukkitTask respawnTask;
    private BukkitTask collapseAlarmTask;
    private BukkitTask collapseCloseTask;

    private double activeShrinkAmount;
    private int    activeShrinkInterval;
    private int    activeSpawnInterval;
    private int    activeMaxMobs;
    private int    activeBaseSpawnCount;
    private long   activeFireInterval;

    private boolean isCollapsing = false;

    private final Random random = new Random();

    public ZoneManager(OfflineArena plugin) {
        this.plugin = plugin;
    }

    public void scheduleInitialZone() {
        Bukkit.getScheduler().runTaskLater(plugin, this::openZone, 200L);
    }

    public void openZone() {
        if (activeZone != null) return;

        if (Bukkit.getWorlds().isEmpty()) {
            plugin.getLogger().warning("No worlds loaded: cannot open Dead Zone.");
            return;
        }
        World world = Bukkit.getWorlds().get(0);

        double spawnRadius = plugin.getConfigManager().getSpawnRadius();
        double originX     = plugin.getConfigManager().getOriginX();
        double originZ     = plugin.getConfigManager().getOriginZ();

        double angle = random.nextDouble() * 2 * Math.PI;
        double dist  = random.nextDouble() * spawnRadius;
        double cx    = originX + dist * Math.cos(angle);
        double cz    = originZ + dist * Math.sin(angle);

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

        int mobMin = plugin.getConfigManager().getMaxTotalMobsMin();
        int mobMax = plugin.getConfigManager().getMaxTotalMobsMax();
        activeMaxMobs = mobMin + random.nextInt(Math.max(1, mobMax - mobMin + 1));

        int bscMin = plugin.getConfigManager().getBaseSpawnCountMin();
        int bscMax = plugin.getConfigManager().getBaseSpawnCountMax();
        activeBaseSpawnCount = bscMin + random.nextInt(Math.max(1, bscMax - bscMin + 1));

        int fiMin = plugin.getConfigManager().getFireIntervalMin();
        int fiMax = plugin.getConfigManager().getFireIntervalMax();
        activeFireInterval = fiMin + random.nextInt(Math.max(1, fiMax - fiMin + 1));

        isCollapsing = false;
        activeZone = new DeadZone(new Location(world, cx, 64, cz), initialRadius);

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

        cancelCollapseTasks();
        isCollapsing = false;

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
        long shrinkTicks = (long) activeShrinkInterval * 20L;
        long mobTicks    = (long) activeSpawnInterval  * 20L;
        long tokenTicks  = (long) plugin.getConfigManager().getTokenRewardInterval() * 20L;

        shrinkTask   = Bukkit.getScheduler().runTaskTimer(plugin, this::tickShrink,    shrinkTicks, shrinkTicks);
        mobTask      = Bukkit.getScheduler().runTaskTimer(plugin, this::tickMobs,      mobTicks,    mobTicks);
        tokenTask    = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTokens,    tokenTicks,  tokenTicks);
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickParticles, 40L,         40L);
        fireTask     = Bukkit.getScheduler().runTaskTimer(plugin, this::tickFire, activeFireInterval, activeFireInterval);
    }

    private void tickShrink() {
        if (activeZone == null || isCollapsing) return;

        boolean phaseChanged = activeZone.shrink(activeShrinkAmount);

        if (activeZone.getCurrentRadius() <= plugin.getConfigManager().getMinRadius()) {
            startCollapse();
            return;
        }

        if (phaseChanged) onPhaseChange(activeZone.getCurrentPhase());
        updateBossBar();
    }

    private void tickMobs()      { if (activeZone != null) plugin.getMobSpawnManager().spawnMobs(activeZone); }
    private void tickTokens()    { if (activeZone != null) plugin.getTokenRewardManager().rewardPlayers(activeZone); }
    private void tickParticles() { if (activeZone != null) plugin.getParticleManager().drawZoneBorder(activeZone); }

    private void tickFire() {
        if (activeZone == null) return;
        World world = activeZone.getCenter().getWorld();
        if (world == null) return;

        ZonePhase phase = activeZone.getCurrentPhase();

        int fireCount = randRange(switch (phase) {
            case AWAKENING    -> new int[]{plugin.getConfigManager().getFireAwakeningMin(),    plugin.getConfigManager().getFireAwakeningMax()};
            case INTENSIFYING -> new int[]{plugin.getConfigManager().getFireIntensifyingMin(), plugin.getConfigManager().getFireIntensifyingMax()};
            case CRITICAL     -> new int[]{plugin.getConfigManager().getFireCriticalMin(),     plugin.getConfigManager().getFireCriticalMax()};
            case COLLAPSE     -> new int[]{plugin.getConfigManager().getFireCollapseMin(),     plugin.getConfigManager().getFireCollapseMax()};
        });

        for (int i = 0; i < fireCount; i++) {
            Location loc = randomSurfaceInZone(0.75);
            if (loc == null) continue;
            Block surface = loc.getBlock();
            Block above   = surface.getRelative(0, 1, 0);
            if (surface.getType().isSolid() && above.getType() == Material.AIR) {
                above.setType(Material.FIRE);
            }
        }

        int explosions = switch (phase) {
            case AWAKENING    -> 0;
            case INTENSIFYING -> randRange(plugin.getConfigManager().getExplosionsIntensifyingMin(), plugin.getConfigManager().getExplosionsIntensifyingMax());
            case CRITICAL     -> randRange(plugin.getConfigManager().getExplosionsCriticalMin(),     plugin.getConfigManager().getExplosionsCriticalMax());
            case COLLAPSE     -> 0;
        };

        for (int i = 0; i < explosions; i++) {
            Location loc = randomSurfaceInZone(0.7);
            if (loc != null) world.createExplosion(loc, 2.0f, true, true);
        }
    }

    private void cancelTasks() {
        if (shrinkTask   != null) { shrinkTask.cancel();   shrinkTask   = null; }
        if (mobTask      != null) { mobTask.cancel();      mobTask      = null; }
        if (tokenTask    != null) { tokenTask.cancel();    tokenTask    = null; }
        if (particleTask != null) { particleTask.cancel(); particleTask = null; }
        if (fireTask     != null) { fireTask.cancel();     fireTask     = null; }
    }

    private void cancelCollapseTasks() {
        if (collapseAlarmTask != null) { collapseAlarmTask.cancel(); collapseAlarmTask = null; }
        if (collapseCloseTask != null) { collapseCloseTask.cancel(); collapseCloseTask = null; }
    }

    private void startCollapse() {
        isCollapsing = true;

        Bukkit.getServer().broadcast(
            Component.text("The Dead Zone is collapsing...", NamedTextColor.DARK_RED)
        );

        collapseAlarmTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickCollapseAlarm, 0L, 100L);

        int cdMin = plugin.getConfigManager().getCollapseDelayMin();
        int cdMax = plugin.getConfigManager().getCollapseDelayMax();
        int delay = cdMin + random.nextInt(Math.max(1, cdMax - cdMin + 1));
        collapseCloseTask = Bukkit.getScheduler().runTaskLater(plugin, () -> closeZone(true), (long) delay * 20L);
    }

    private void tickCollapseAlarm() {
        if (activeZone == null) return;
        World world = activeZone.getCenter().getWorld();
        if (world == null) return;

        for (java.util.UUID uuid : activeZone.getPlayersInZone()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.7f, 0.4f);
        }

        Location explodeLoc = randomSurfaceInZone(0.6);
        if (explodeLoc != null) {
            world.createExplosion(explodeLoc, 2.5f, true, true);
        }

        int fires = 3 + random.nextInt(3);
        for (int i = 0; i < fires; i++) {
            Location loc = randomSurfaceInZone(0.8);
            if (loc == null) continue;
            Block surface = loc.getBlock();
            Block above   = surface.getRelative(0, 1, 0);
            if (surface.getType().isSolid() && above.getType() == Material.AIR) {
                above.setType(Material.FIRE);
            }
        }
    }

    private void onPhaseChange(ZonePhase phase) {
        Sound phaseSound = switch (phase) {
            case INTENSIFYING -> Sound.ENTITY_ELDER_GUARDIAN_AMBIENT;
            case CRITICAL     -> Sound.ENTITY_WITHER_AMBIENT;
            case COLLAPSE     -> Sound.ENTITY_WITHER_SPAWN;
            default           -> Sound.ENTITY_WITHER_AMBIENT;
        };
        float phasePitch = switch (phase) {
            case INTENSIFYING -> 0.8f;
            case CRITICAL     -> 0.6f;
            case COLLAPSE     -> 0.5f;
            default           -> 1.0f;
        };

        for (java.util.UUID uuid : activeZone.getPlayersInZone()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.showTitle(Title.title(
                Component.text(phase.getDisplayName(), phase.getTextColor()).decorate(TextDecoration.BOLD),
                Component.text("The zone is shrinking.", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))
            ));
            p.playSound(p.getLocation(), phaseSound, 1.0f, phasePitch);
            if (phase != ZonePhase.AWAKENING) {
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.7f, phasePitch * 0.8f);
            }
        }
    }

    private void createBossBar() {
        zoneBossBar = Bukkit.createBossBar(buildBossBarTitle(), org.bukkit.boss.BarColor.GREEN, BarStyle.SOLID);
        zoneBossBar.setProgress(1.0);
        zoneBossBar.setVisible(true);
    }

    private void updateBossBar() {
        if (zoneBossBar == null || activeZone == null) return;
        zoneBossBar.setTitle(buildBossBarTitle());
        zoneBossBar.setColor(activeZone.getCurrentPhase().getBarColor());
        zoneBossBar.setProgress(Math.max(0.0, Math.min(1.0, activeZone.getShrinkRatio())));
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

        final Location epicentre = activeZone.getCenter().clone();
        final World    world     = epicentre.getWorld();

        if (world != null) {
            world.createExplosion(epicentre, 10.0f, true, true);
            for (int i = 0; i < 8; i++) {
                double a = random.nextDouble() * 2 * Math.PI;
                double d = 3 + random.nextDouble() * 10;
                world.strikeLightningEffect(epicentre.clone().add(d * Math.cos(a), 0, d * Math.sin(a)));
            }

            for (int i = 0; i < 24; i++) {
                final double a = random.nextDouble() * 2 * Math.PI;
                final double d = 6 + i * 3.5 + random.nextDouble() * 8;
                final long   delay = 2L + i * 3L;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Location loc = epicentre.clone().add(d * Math.cos(a), 0, d * Math.sin(a));
                    world.createExplosion(loc, 4.0f, true, true);
                    world.strikeLightningEffect(loc);
                }, delay);
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 0.5f);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.3f);
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.8f, 0.6f);
            }
        }, 10L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.25f);
            }
        }, 30L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.AMBIENT_CAVE, 0.8f, 0.3f);
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 0.2f);
            }
        }, 55L);

        if (plugin.getConfigManager().isDiscordAnnounceClose()) {
            plugin.getDiscordManager().sendFormatted(
                "Dead Zone",
                "The Dead Zone has collapsed.",
                Color.DARK_GRAY
            );
        }
    }

    private Location randomSurfaceInZone(double radiusFraction) {
        if (activeZone == null) return null;
        Location center = activeZone.getCenter();
        World    world  = center.getWorld();
        if (world == null) return null;

        double r     = activeZone.getCurrentRadius() * radiusFraction;
        double angle = random.nextDouble() * 2 * Math.PI;
        double dist  = random.nextDouble() * r;
        double x     = center.getX() + dist * Math.cos(angle);
        double z     = center.getZ() + dist * Math.sin(angle);
        int    y     = world.getHighestBlockYAt((int) x, (int) z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        return new Location(world, x, y, z);
    }

    public void shutdown() {
        cancelTasks();
        cancelCollapseTasks();
        if (respawnTask != null) { respawnTask.cancel(); respawnTask = null; }
        if (activeZone  != null) {
            plugin.getMobSpawnManager().clearZoneMobs(activeZone);
            activeZone = null;
        }
        destroyBossBar();
    }

    private int randRange(int min, int max) {
        return min + random.nextInt(Math.max(1, max - min + 1));
    }

    private int randRange(int[] minMax) {
        return randRange(minMax[0], minMax[1]);
    }

    public DeadZone getActiveZone()          { return activeZone; }
    public boolean  hasActiveZone()          { return activeZone != null; }
    public BossBar  getZoneBossBar()         { return zoneBossBar; }
    public int      getActiveMaxMobs()       { return activeMaxMobs; }
    public int      getActiveBaseSpawnCount(){ return activeBaseSpawnCount; }
}
