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

    /** Max horizontal distance from the border to receive particles. */
    private static final double MAX_BORDER_DIST = 128.0;

    /** Degrees between ring points — 3 degrees = 120 points per ring, very smooth. */
    private static final int ANGLE_STEP_DEG = 3;

    /**
     * Y offsets relative to player eye level at which rings are drawn.
     * Dense spacing (4 blocks) over a 28-block span creates a solid wall appearance.
     */
    private static final double[] Y_OFFSETS = { -12, -8, -4, 0, 4, 8, 12, 16 };

    /** How much dust particles spread at each point (makes clusters more visible). */
    private static final double SPREAD = 0.25;

    /** Players within this many blocks of the border edge hear the proximity sound. */
    private static final double SOUND_DIST = 30.0;

    public ParticleManager() {}

    public void drawZoneBorder(DeadZone zone) {
        Location center = zone.getCenter();
        double   radius = zone.getCurrentRadius();
        World    world  = center.getWorld();
        if (world == null || radius <= 0) return;

        ZonePhase            phase = zone.getCurrentPhase();
        Particle.DustOptions dust  = dustFor(phase);

        for (Player player : world.getPlayers()) {
            double dx   = player.getLocation().getX() - center.getX();
            double dz   = player.getLocation().getZ() - center.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (Math.abs(dist - radius) > MAX_BORDER_DIST) continue;

            double eyeY = player.getEyeLocation().getY();
            for (double yOff : Y_OFFSETS) {
                drawRingToPlayer(player, center, radius, eyeY + yOff, dust, phase);
            }

            // Proximity ambient sound — grows louder and more ominous the closer you are
            double borderDist = Math.abs(dist - radius);
            if (borderDist < SOUND_DIST) {
                float closeness = (float) (1.0 - borderDist / SOUND_DIST); // 0..1, 1 = right on border
                playProximitySound(player, phase, closeness);
            }
        }
    }

    private void drawRingToPlayer(Player player, Location center, double radius,
                                   double y, Particle.DustOptions dust, ZonePhase phase) {
        World world = center.getWorld();
        for (int deg = 0; deg < 360; deg += ANGLE_STEP_DEG) {
            double rad = Math.toRadians(deg);
            double x   = center.getX() + radius * Math.cos(rad);
            double z   = center.getZ() + radius * Math.sin(rad);
            Location loc = new Location(world, x, y, z);

            // Spawn with a small spread so each point looks like a cluster
            player.spawnParticle(Particle.DUST, loc, 2, SPREAD, SPREAD, SPREAD, 0, dust);

            if (phase == ZonePhase.CRITICAL || phase == ZonePhase.COLLAPSE) {
                if (deg % 9 == 0) {
                    player.spawnParticle(Particle.FLAME, loc, 1, SPREAD, SPREAD, SPREAD, 0.01);
                }
            }
            if (phase == ZonePhase.COLLAPSE && deg % 12 == 0) {
                player.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 1, SPREAD, SPREAD, SPREAD, 0.01);
            }
        }
    }

    private void playProximitySound(Player player, ZonePhase phase, float closeness) {
        // Volume scales with closeness; capped so it stays atmospheric rather than loud
        float volume = 0.1f + closeness * 0.25f;

        switch (phase) {
            case AWAKENING -> {
                // Distant portal hum — eerie but subtle
                player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, volume, 0.5f);
            }
            case INTENSIFYING -> {
                player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, volume, 0.4f);
                if (closeness > 0.6f) {
                    player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, volume * 0.6f, 0.5f);
                }
            }
            case CRITICAL -> {
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, volume * 0.7f, 0.5f);
                player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, volume * 0.5f, 0.3f);
            }
            case COLLAPSE -> {
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, volume, 0.3f);
                if (closeness > 0.5f) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_AMBIENT, volume * 0.5f, 0.3f);
                }
            }
        }
    }

    private Particle.DustOptions dustFor(ZonePhase phase) {
        Color  color = switch (phase) {
            case AWAKENING    -> Color.fromRGB(0,   220, 200);
            case INTENSIFYING -> Color.fromRGB(255, 210, 0);
            case CRITICAL     -> Color.fromRGB(255, 90,  0);
            case COLLAPSE     -> Color.fromRGB(255, 0,   0);
        };
        float size = switch (phase) {
            case AWAKENING    -> 1.5f;
            case INTENSIFYING -> 2.0f;
            case CRITICAL     -> 2.5f;
            case COLLAPSE     -> 3.0f;
        };
        return new Particle.DustOptions(color, size);
    }
}
