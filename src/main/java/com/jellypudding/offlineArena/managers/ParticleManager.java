package com.jellypudding.offlineArena.managers;

import com.jellypudding.offlineArena.zone.DeadZone;
import com.jellypudding.offlineArena.zone.ZonePhase;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class ParticleManager {

    /** Max horizontal distance from the border to receive particles. */
    private static final double MAX_BORDER_DIST = 96.0;

    /** Degrees between each particle point around the ring. Lower = smoother, more packets. */
    private static final int ANGLE_STEP_DEG = 5;   // 72 points per ring

    /** Y offsets (relative to eye level) at which rings are drawn. */
    private static final double[] Y_OFFSETS = { -20, -10, 0, 10, 20 };

    public ParticleManager() {}

    public void drawZoneBorder(DeadZone zone) {
        Location center = zone.getCenter();
        double   radius = zone.getCurrentRadius();
        World    world  = center.getWorld();
        if (world == null || radius <= 0) return;

        ZonePhase         phase = zone.getCurrentPhase();
        Particle.DustOptions dust = dustFor(phase);

        for (Player player : world.getPlayers()) {
            // Horizontal-only distance to the border
            double dx   = player.getLocation().getX() - center.getX();
            double dz   = player.getLocation().getZ() - center.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (Math.abs(dist - radius) > MAX_BORDER_DIST) continue;

            double eyeY = player.getEyeLocation().getY();
            for (double yOff : Y_OFFSETS) {
                drawRingToPlayer(player, center, radius, eyeY + yOff, dust, phase);
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

            player.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, dust);

            // Flame accent for CRITICAL and COLLAPSE
            if ((phase == ZonePhase.CRITICAL || phase == ZonePhase.COLLAPSE) && deg % 15 == 0) {
                player.spawnParticle(Particle.FLAME, loc, 1, 0.0, 0.0, 0.0, 0.02);
            }
            // Soul fire for COLLAPSE
            if (phase == ZonePhase.COLLAPSE && deg % 20 == 0) {
                player.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 1, 0.0, 0.0, 0.0, 0.02);
            }
        }
    }

    private Particle.DustOptions dustFor(ZonePhase phase) {
        Color  color = switch (phase) {
            case AWAKENING    -> Color.fromRGB(0,   210, 200); // cyan-teal
            case INTENSIFYING -> Color.fromRGB(255, 210, 0);   // yellow
            case CRITICAL     -> Color.fromRGB(255, 90,  0);   // orange
            case COLLAPSE     -> Color.fromRGB(255, 0,   0);   // red
        };
        float size = switch (phase) {
            case AWAKENING    -> 1.0f;
            case INTENSIFYING -> 1.3f;
            case CRITICAL     -> 1.6f;
            case COLLAPSE     -> 2.0f;
        };
        return new Particle.DustOptions(color, size);
    }
}
