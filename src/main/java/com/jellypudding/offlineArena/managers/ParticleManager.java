package com.jellypudding.offlineArena.managers;

import com.jellypudding.offlineArena.zone.DeadZone;
import com.jellypudding.offlineArena.zone.ZonePhase;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class ParticleManager {

    private static final double MAX_BORDER_DIST = 128.0;
    private static final double SOUND_DIST      = 30.0;
    private static final double SPREAD          = 0.3;

    private static final int    RING_ANGLE_STEP = 2;
    private static final int    RING_COUNT      = 360 / RING_ANGLE_STEP;
    private static final double[] RING_COS      = new double[RING_COUNT];
    private static final double[] RING_SIN      = new double[RING_COUNT];

    private static final double[] Y_OFFSETS = { -21, -18, -15, -12, -9, -6, -3, 0, 3, 6, 9, 12, 15, 18, 21, 24 };

    private static final int    PILLAR_ANGLE_STEP = 30;
    private static final int    PILLAR_COUNT      = 360 / PILLAR_ANGLE_STEP;
    private static final double[] PILLAR_COS      = new double[PILLAR_COUNT];
    private static final double[] PILLAR_SIN      = new double[PILLAR_COUNT];

    private static final double PILLAR_Y_START = -30;
    private static final double PILLAR_Y_END   =  70;
    private static final double PILLAR_Y_STEP  =   3;

    // ── Cap band geometry — 3 rings drawn at/near each height boundary ────────
    // Reuses RING_COS/SIN so density exactly matches the wall rings.
    // offsets[0] = at the boundary, offsets[1/2] = steps inside the zone
    private static final double[] CAP_Y_OFFSETS = { 0, 2, 4 };
    private static final double   CAP_VERT_DIST = 60.0;

    // ── Cached dust options ───────────────────────────────────────────────────
    private static final Particle.DustOptions DUST_AWAKENING    = new Particle.DustOptions(Color.fromRGB(0,   220, 200), 1.5f);
    private static final Particle.DustOptions DUST_INTENSIFYING = new Particle.DustOptions(Color.fromRGB(255, 210,   0), 2.0f);
    private static final Particle.DustOptions DUST_CRITICAL     = new Particle.DustOptions(Color.fromRGB(255,  90,   0), 2.5f);
    private static final Particle.DustOptions DUST_COLLAPSE     = new Particle.DustOptions(Color.fromRGB(255,   0,   0), 3.0f);

    static {
        for (int i = 0; i < RING_COUNT; i++) {
            double r = Math.toRadians(i * RING_ANGLE_STEP);
            RING_COS[i] = Math.cos(r);
            RING_SIN[i] = Math.sin(r);
        }
        for (int i = 0; i < PILLAR_COUNT; i++) {
            double r = Math.toRadians(i * PILLAR_ANGLE_STEP);
            PILLAR_COS[i] = Math.cos(r);
            PILLAR_SIN[i] = Math.sin(r);
        }
    }

    public ParticleManager() {}

    public void drawZoneBorder(DeadZone zone) {
        double radius    = zone.getCurrentRadius();
        World  world     = zone.getCenter().getWorld();
        if (world == null || radius <= 0) return;

        double    cx        = zone.getCenter().getX();
        double    cz        = zone.getCenter().getZ();
        double    heightMin = zone.getHeightMin();
        double    heightMax = zone.getHeightMax();
        ZonePhase phase     = zone.getCurrentPhase();
        Particle.DustOptions dust = dustFor(phase);

        for (Player player : world.getPlayers()) {
            Location playerLoc = player.getLocation();
            double dx   = playerLoc.getX() - cx;
            double dz   = playerLoc.getZ() - cz;
            double dist = Math.sqrt(dx * dx + dz * dz);

            boolean insideZone  = dist <= radius;
            boolean nearBorder  = Math.abs(dist - radius) <= MAX_BORDER_DIST;

            // Nothing to render for players far outside the zone
            if (!insideZone && !nearBorder) continue;

            double eyeY    = playerLoc.getY() + player.getEyeHeight();
            double playerY = playerLoc.getY();

            // Wall rings and pillars only for players near the border
            if (nearBorder) {
                drawRings(player, world, cx, cz, radius, eyeY, heightMin, heightMax, dust, phase);
                drawPillars(player, world, cx, cz, radius, eyeY, heightMin, heightMax, dust, phase);
            }

            // Cap discs for all players inside (or near) the zone regardless of horizontal distance to wall
            drawCapRings(player, world, cx, cz, radius, playerY, heightMin, heightMax, dust, phase);

            if (nearBorder) {
                double borderDist = Math.abs(dist - radius);
                if (borderDist < SOUND_DIST) {
                    float closeness = (float) (1.0 - borderDist / SOUND_DIST);
                    playProximitySound(player, playerLoc, phase, closeness);
                }
            }
        }
    }

    /** Horizontal rings clamped to [heightMin, heightMax], drawn at eye-relative Y offsets. */
    private static void drawRings(Player player, World world,
                                   double cx, double cz, double radius, double eyeY,
                                   double heightMin, double heightMax,
                                   Particle.DustOptions dust, ZonePhase phase) {
        boolean flame     = phase == ZonePhase.CRITICAL || phase == ZonePhase.COLLAPSE;
        boolean soulFlame = phase == ZonePhase.COLLAPSE;
        Location loc = new Location(world, 0, 0, 0);

        for (double yOff : Y_OFFSETS) {
            double y = eyeY + yOff;
            if (y < heightMin || y > heightMax) continue;
            loc.setY(y);
            for (int i = 0; i < RING_COUNT; i++) {
                loc.setX(cx + radius * RING_COS[i]);
                loc.setZ(cz + radius * RING_SIN[i]);
                player.spawnParticle(Particle.DUST, loc, 3, SPREAD, SPREAD, SPREAD, 0, dust);
                int deg = i * RING_ANGLE_STEP;
                if (flame     && deg % 8  == 0) player.spawnParticle(Particle.FLAME,          loc, 1, SPREAD, SPREAD, SPREAD, 0.01);
                if (soulFlame && deg % 10 == 0) player.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 1, SPREAD, SPREAD, SPREAD, 0.01);
            }
        }
    }

    /** Vertical beacon pillars at 12 evenly-spaced angles, Y clamped to [heightMin, heightMax]. */
    private static void drawPillars(Player player, World world,
                                     double cx, double cz, double radius, double eyeY,
                                     double heightMin, double heightMax,
                                     Particle.DustOptions dust, ZonePhase phase) {
        boolean flame     = phase == ZonePhase.CRITICAL || phase == ZonePhase.COLLAPSE;
        boolean soulFlame = phase == ZonePhase.COLLAPSE;
        Location loc = new Location(world, 0, 0, 0);

        for (int i = 0; i < PILLAR_COUNT; i++) {
            loc.setX(cx + radius * PILLAR_COS[i]);
            loc.setZ(cz + radius * PILLAR_SIN[i]);
            for (double yOff = PILLAR_Y_START; yOff <= PILLAR_Y_END; yOff += PILLAR_Y_STEP) {
                double y = eyeY + yOff;
                if (y < heightMin || y > heightMax) continue;
                loc.setY(y);
                player.spawnParticle(Particle.DUST, loc, 1, 0.1, 0.1, 0.1, 0, dust);
                if (flame)     player.spawnParticle(Particle.FLAME,          loc, 1, 0.05, 0.05, 0.05, 0.005);
                if (soulFlame) player.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 1, 0.05, 0.05, 0.05, 0.005);
            }
        }
    }

    /**
     * Draws a band of 3 wall-style horizontal rings at each height boundary.
     * Each ring uses the same RING_COS/SIN table as the walls so density is identical.
     * Shown to players within CAP_VERT_DIST blocks of each boundary.
     */
    private static void drawCapRings(Player player, World world,
                                      double cx, double cz, double radius, double playerY,
                                      double heightMin, double heightMax,
                                      Particle.DustOptions dust, ZonePhase phase) {
        boolean showFloor   = playerY < heightMin + CAP_VERT_DIST;
        boolean showCeiling = playerY > heightMax - CAP_VERT_DIST;
        if (!showFloor && !showCeiling) return;

        boolean flame     = phase == ZonePhase.CRITICAL || phase == ZonePhase.COLLAPSE;
        boolean soulFlame = phase == ZonePhase.COLLAPSE;
        Location loc = new Location(world, 0, 0, 0);

        // +1 = rings step upward into the zone from the floor; -1 = downward from the ceiling
        if (showFloor)   renderCapBand(player, loc, cx, cz, radius, heightMin, +1, dust, flame, soulFlame);
        if (showCeiling) renderCapBand(player, loc, cx, cz, radius, heightMax, -1, dust, flame, soulFlame);
    }

    private static void renderCapBand(Player player, Location loc,
                                       double cx, double cz, double radius,
                                       double boundaryY, int direction,
                                       Particle.DustOptions dust, boolean flame, boolean soulFlame) {
        for (double yOff : CAP_Y_OFFSETS) {
            loc.setY(boundaryY + direction * yOff);
            for (int i = 0; i < RING_COUNT; i++) {
                loc.setX(cx + radius * RING_COS[i]);
                loc.setZ(cz + radius * RING_SIN[i]);
                player.spawnParticle(Particle.DUST, loc, 3, SPREAD, SPREAD, SPREAD, 0, dust);
                int deg = i * RING_ANGLE_STEP;
                if (flame     && deg % 8  == 0) player.spawnParticle(Particle.FLAME,          loc, 1, SPREAD, SPREAD, SPREAD, 0.01);
                if (soulFlame && deg % 10 == 0) player.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 1, SPREAD, SPREAD, SPREAD, 0.01);
            }
        }
    }

    private static void playProximitySound(Player player, Location playerLoc,
                                            ZonePhase phase, float closeness) {
        float volume = 0.12f + closeness * 0.3f;
        switch (phase) {
            case AWAKENING -> player.playSound(playerLoc, Sound.BLOCK_PORTAL_AMBIENT, volume, 0.5f);
            case INTENSIFYING -> {
                player.playSound(playerLoc, Sound.BLOCK_PORTAL_AMBIENT, volume, 0.4f);
                if (closeness > 0.6f) player.playSound(playerLoc, Sound.AMBIENT_CAVE, volume * 0.6f, 0.5f);
            }
            case CRITICAL -> {
                player.playSound(playerLoc, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, volume * 0.7f, 0.45f);
                if (closeness > 0.5f) player.playSound(playerLoc, Sound.ENTITY_GHAST_AMBIENT, volume * 0.5f, 0.35f);
            }
            case COLLAPSE -> {
                player.playSound(playerLoc, Sound.ENTITY_WITHER_AMBIENT, volume, 0.3f);
                if (closeness > 0.5f) player.playSound(playerLoc, Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, volume * 0.6f, 0.3f);
            }
        }
    }

    private static Particle.DustOptions dustFor(ZonePhase phase) {
        return switch (phase) {
            case AWAKENING    -> DUST_AWAKENING;
            case INTENSIFYING -> DUST_INTENSIFYING;
            case CRITICAL     -> DUST_CRITICAL;
            case COLLAPSE     -> DUST_COLLAPSE;
        };
    }
}
