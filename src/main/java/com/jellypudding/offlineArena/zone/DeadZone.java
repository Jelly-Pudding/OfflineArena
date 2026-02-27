package com.jellypudding.offlineArena.zone;

import org.bukkit.Location;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DeadZone {

    private final Location center;
    private double currentRadius;
    private final double initialRadius;
    private ZonePhase currentPhase;

    private final Set<UUID> playersInZone = new HashSet<>();
    private final Set<UUID> zoneMobs      = new HashSet<>();

    public DeadZone(Location center, double initialRadius) {
        this.center        = center.clone();
        this.currentRadius = initialRadius;
        this.initialRadius = initialRadius;
        this.currentPhase  = ZonePhase.AWAKENING;
    }

    public boolean isInside(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().equals(center.getWorld())) return false;
        double dx = loc.getX() - center.getX();
        double dz = loc.getZ() - center.getZ();
        return (dx * dx + dz * dz) <= (currentRadius * currentRadius);
    }

    /** Horizontal distance from loc to the zone border (negative = inside). */
    public double distanceToBorder(Location loc) {
        double dx = loc.getX() - center.getX();
        double dz = loc.getZ() - center.getZ();
        return Math.sqrt(dx * dx + dz * dz) - currentRadius;
    }

    /**
     * Shrinks the zone.
     * @return true if this shrink caused a phase transition.
     */
    public boolean shrink(double amount) {
        currentRadius = Math.max(0, currentRadius - amount);
        ZonePhase newPhase = ZonePhase.fromRatio(getShrinkRatio());
        if (newPhase != currentPhase) {
            currentPhase = newPhase;
            return true;
        }
        return false;
    }

    /** 1.0 = full size, 0.0 = gone. */
    public double getShrinkRatio() {
        return initialRadius > 0 ? currentRadius / initialRadius : 0;
    }

    // Player tracking

    public void addPlayer(UUID uuid)    { playersInZone.add(uuid); }
    public void removePlayer(UUID uuid) { playersInZone.remove(uuid); }
    public Set<UUID> getPlayersInZone() { return Collections.unmodifiableSet(playersInZone); }

    // Mob tracking

    public void trackMob(UUID uuid)   { zoneMobs.add(uuid); }
    public void untrackMob(UUID uuid) { zoneMobs.remove(uuid); }
    public Set<UUID> getZoneMobs()    { return Collections.unmodifiableSet(zoneMobs); }
    public int getMobCount()          { return zoneMobs.size(); }

    // Getters

    public Location  getCenter()        { return center.clone(); }
    public double    getCurrentRadius() { return currentRadius; }
    public double    getInitialRadius() { return initialRadius; }
    public ZonePhase getCurrentPhase()  { return currentPhase; }
}
