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

    private boolean isCollapsing       = false;
    private int     shrinkCountdownSecs = 0;

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

        double cx = originX, cz = originZ;
        int bestWater = Integer.MAX_VALUE;
        for (int attempt = 0; attempt < 8; attempt++) {
            double a  = random.nextDouble() * 2 * Math.PI;
            double d  = random.nextDouble() * spawnRadius;
            double tx = originX + d * Math.cos(a);
            double tz = originZ + d * Math.sin(a);
            int water = countOceanSamples(world, tx, tz);
            if (water < bestWater) {
                bestWater = water;
                cx = tx;
                cz = tz;
                if (water == 0) break;
            }
        }

        double rMin = plugin.getConfigManager().getInitialRadiusMin();
        double rMax = plugin.getConfigManager().getInitialRadiusMax();
        double initialRadius = rMin + random.nextDouble() * (rMax - rMin);

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
        activeZone = new DeadZone(new Location(world, cx, 64, cz), initialRadius,
            plugin.getConfigManager().getZoneHeightMin(),
            plugin.getConfigManager().getZoneHeightMax());

        for (Player p : world.getPlayers()) {
            if (activeZone.isInside(p.getLocation())) {
                activeZone.addPlayer(p.getUniqueId());
            }
        }

        createBossBar();
        broadcastZoneOpen();
        startTasks();

        plugin.getLogger().info(String.format(
            "Dead Zone opened at (%.0f, %.0f) radius=%.0f shrinkAmount=%.1f spawnInterval=%ds",
            cx, cz, initialRadius, activeShrinkAmount, activeSpawnInterval));
    }

    public void closeZone(boolean natural) {
        if (activeZone == null) return;

        cancelCollapseTasks();
        isCollapsing = false;

        for (java.util.UUID uuid : activeZone.getPlayersInZone()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) plugin.getVelocityGuardManager().disableFlightEnforcement(p);
        }

        plugin.getTokenRewardManager().rewardZoneClose(activeZone);
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
        long mobTicks = (long) activeSpawnInterval * 20L;

        scheduleNextShrink();
        mobTask      = Bukkit.getScheduler().runTaskTimer(plugin, this::tickMobs,      mobTicks,    mobTicks);
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickParticles, 20L, 20L);
        fireTask     = Bukkit.getScheduler().runTaskTimer(plugin, this::tickFire, activeFireInterval, activeFireInterval);
        scheduleNextTokenReward();
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
        scheduleNextShrink();
    }

    private void tickMobs()      { if (activeZone != null) plugin.getMobSpawnManager().spawnMobs(activeZone); }
    private void tickParticles() {
        if (activeZone == null) return;
        plugin.getParticleManager().drawZoneBorder(activeZone);
        if (!isCollapsing && shrinkCountdownSecs > 0) shrinkCountdownSecs--;
        updateBossBar();
    }

    private void scheduleNextShrink() {
        if (activeZone == null || isCollapsing) return;
        int min, max;
        switch (activeZone.getCurrentPhase()) {
            case AWAKENING    -> { min = plugin.getConfigManager().getShrinkIntervalAwakeningMin();    max = plugin.getConfigManager().getShrinkIntervalAwakeningMax(); }
            case INTENSIFYING -> { min = plugin.getConfigManager().getShrinkIntervalIntensifyingMin(); max = plugin.getConfigManager().getShrinkIntervalIntensifyingMax(); }
            case CRITICAL     -> { min = plugin.getConfigManager().getShrinkIntervalCriticalMin();     max = plugin.getConfigManager().getShrinkIntervalCriticalMax(); }
            case COLLAPSE     -> { min = plugin.getConfigManager().getShrinkIntervalCollapseMin();     max = plugin.getConfigManager().getShrinkIntervalCollapseMax(); }
            default           -> { min = 20; max = 45; }
        }
        activeShrinkInterval = min + random.nextInt(Math.max(1, max - min + 1));
        shrinkCountdownSecs  = activeShrinkInterval;
        shrinkTask = Bukkit.getScheduler().runTaskLater(plugin, this::tickShrink, (long) activeShrinkInterval * 20L);
    }

    private void scheduleNextTokenReward() {
        if (activeZone == null) return;
        double ratio       = activeZone.getShrinkRatio();
        int    minInterval = plugin.getConfigManager().getTokenIntervalMin();
        int    maxInterval = plugin.getConfigManager().getTokenIntervalMax();
        int    intervalSecs = (int) Math.round(minInterval + (maxInterval - minInterval) * ratio);
        tokenTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (activeZone == null) return;
            plugin.getTokenRewardManager().rewardPlayers(activeZone);
            scheduleNextTokenReward();
        }, (long) intervalSecs * 20L);
    }

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
            case AWAKENING    -> randRange(plugin.getConfigManager().getExplosionsAwakeningMin(),    plugin.getConfigManager().getExplosionsAwakeningMax());
            case INTENSIFYING -> randRange(plugin.getConfigManager().getExplosionsIntensifyingMin(), plugin.getConfigManager().getExplosionsIntensifyingMax());
            case CRITICAL     -> randRange(plugin.getConfigManager().getExplosionsCriticalMin(),     plugin.getConfigManager().getExplosionsCriticalMax());
            case COLLAPSE     -> randRange(plugin.getConfigManager().getExplosionsCollapseMin(),     plugin.getConfigManager().getExplosionsCollapseMax());
        };

        double pwrMin        = plugin.getConfigManager().getExplosionPowerMin();
        double pwrMax        = plugin.getConfigManager().getExplosionPowerMax();
        double lightningChance = switch (phase) {
            case CRITICAL -> plugin.getConfigManager().getLightningChanceCritical();
            case COLLAPSE -> plugin.getConfigManager().getLightningChanceCollapse();
            default       -> 0.0;
        };
        for (int i = 0; i < explosions; i++) {
            Location loc = randomSurfaceInZone(0.7);
            if (loc != null) {
                float power = (float) (pwrMin + random.nextDouble() * (pwrMax - pwrMin));
                world.createExplosion(loc, power, true, true);
                if (lightningChance > 0 && random.nextDouble() < lightningChance) {
                    world.strikeLightning(loc);
                }
            }
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
                Component.empty(),
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
        String c     = p.getColorCode();
        String phase = c + "Dead Zone §8| " + c + p.name();
        if (isCollapsing) return phase + " §8| §cCollapsing";
        return phase + " §8| " + c + "Shrinks in " + shrinkCountdownSecs + "s";
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
            int    count          = randRange(plugin.getConfigManager().getFinaleExplosionsMin(), plugin.getConfigManager().getFinaleExplosionsMax());
            double pwrMin         = plugin.getConfigManager().getFinaleExplosionPowerMin();
            double pwrMax         = plugin.getConfigManager().getFinaleExplosionPowerMax();
            double lightningChance = plugin.getConfigManager().getLightningChanceCollapse();
            double scatter        = Math.min(30.0, activeZone.getCurrentRadius());

            for (int i = 0; i < count; i++) {
                double a   = random.nextDouble() * 2 * Math.PI;
                double d   = random.nextDouble() * scatter;
                float  pwr = (float) (pwrMin + random.nextDouble() * (pwrMax - pwrMin));
                Location loc = epicentre.clone().add(d * Math.cos(a), 0, d * Math.sin(a));
                world.createExplosion(loc, pwr, true, true);
                if (random.nextDouble() < lightningChance) world.strikeLightning(loc);
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

    private int countOceanSamples(World world, double cx, double cz) {
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                String biome = world.getBiome((int) cx + dx * 60, 64, (int) cz + dz * 60).getKey().getKey();
                if (biome.contains("ocean") || biome.equals("river") || biome.equals("frozen_river")) count++;
            }
        }
        return count;
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
