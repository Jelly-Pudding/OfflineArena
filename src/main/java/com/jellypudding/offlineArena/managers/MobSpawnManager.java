package com.jellypudding.offlineArena.managers;

import com.jellypudding.offlineArena.OfflineArena;
import com.jellypudding.offlineArena.zone.DeadZone;
import com.jellypudding.offlineArena.zone.ZonePhase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

/**
 *   AWAKENING    — The Vagabond / The Lurker / Loot Goblin / The Vagrant
 *   INTENSIFYING — The Marauder / The Marksman / The Hunter / The Raider
 *   CRITICAL     — The Defiler / The Alchemist / The Reaper / The Wraith
 *   COLLAPSE     — The Warlord / The Psychopath / The Void (Wither) / The Conjurer
 */
public class MobSpawnManager {

    private static final NamespacedKey ZONE_MOB_KEY = new NamespacedKey("offlinearena", "dead_zone_mob");

    private final OfflineArena plugin;
    private final Random random = new Random();

    public MobSpawnManager(OfflineArena plugin) {
        this.plugin = plugin;
    }

    public void spawnMobs(DeadZone zone) {
        Location zoneCenter = zone.getCenter();
        double   limitR     = zone.getCurrentRadius() * 1.3 + 20;

        for (UUID uuid : new java.util.ArrayList<>(zone.getZoneMobs())) {
            org.bukkit.entity.Entity e = plugin.getServer().getEntity(uuid);
            if (e == null || !e.isValid()) {
                zone.untrackMob(uuid);
                continue;
            }
            Location ml = e.getLocation();
            if (ml.getWorld() != null && ml.getWorld().equals(zoneCenter.getWorld())) {
                double dx = ml.getX() - zoneCenter.getX();
                double dz = ml.getZ() - zoneCenter.getZ();
                if (dx * dx + dz * dz > limitR * limitR) zone.untrackMob(uuid);
            }
        }

        int maxTotal = plugin.getZoneManager().getActiveMaxMobs();
        if (zone.getMobCount() >= maxTotal) return;

        int playersIn = zone.getPlayersInZone().size();
        int capacity  = plugin.getConfigManager().getPlayerCapacity();
        int baseMax   = plugin.getZoneManager().getActiveBaseSpawnCount();

        double occupancy    = Math.min(1.0, (double) playersIn / Math.max(1, capacity));
        int    baseSpawn    = (int) Math.max(1, Math.round(baseMax * (1.0 - occupancy * 0.85)));
        double radiusBonus  = 1.0 + (zone.getShrinkRatio() * 1.5);
        int    toSpawn      = (int) Math.ceil(baseSpawn * zone.getCurrentPhase().getMobPhaseMultiplier() * radiusBonus);
        toSpawn             = Math.min(toSpawn, maxTotal - zone.getMobCount());

        for (int i = 0; i < toSpawn; i++) {
            spawnOneMob(zone);
        }
    }

    private void spawnOneMob(DeadZone zone) {
        if (zone.getCurrentPhase() == ZonePhase.COLLAPSE) {
            long witherCount = zone.getZoneMobs().stream()
                .map(plugin.getServer()::getEntity)
                .filter(e -> e instanceof Wither)
                .count();
            if (witherCount >= plugin.getConfigManager().getMaxWithers()) {
                Location loc = randomLocationInZone(zone);
                if (loc == null) return;
                String[] fallback = {"WARLORD", "PSYCHOPATH", "BANSHEE"};
                Entity mob = buildMob(loc, fallback[random.nextInt(fallback.length)]);
                if (mob != null) tag(mob, zone);
                return;
            }
        }

        Location loc = randomLocationInZone(zone);
        if (loc == null) return;

        String type = randomMobType(zone.getCurrentPhase());
        Entity mob  = buildMob(loc, type);
        if (mob != null) tag(mob, zone);
    }

    private void tag(Entity mob, DeadZone zone) {
        zone.trackMob(mob.getUniqueId());
        mob.getPersistentDataContainer().set(ZONE_MOB_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    private String randomMobType(ZonePhase phase) {
        String[] pool = switch (phase) {
            case AWAKENING    -> new String[]{"VAGABOND", "LURKER", "LOOT_GOBLIN", "VAGRANT"};
            case INTENSIFYING -> new String[]{"MARAUDER", "MARKSMAN", "HUNTER", "RAIDER", "INCENDIARY"};
            case CRITICAL     -> new String[]{"DEFILER", "ALCHEMIST", "REAPER", "WRAITH", "HOLLOW"};
            case COLLAPSE     -> new String[]{"WARLORD", "PSYCHOPATH", "VOID", "CONJURER"};
        };
        return pool[random.nextInt(pool.length)];
    }

    private Entity buildMob(Location loc, String type) {
        return switch (type) {
            case "VAGABOND"   -> spawnVagabond(loc);
            case "LURKER"     -> spawnLurker(loc);
            case "LOOT_GOBLIN"-> spawnLootGoblin(loc);
            case "VAGRANT"    -> spawnVagrant(loc);
            case "MARAUDER"   -> spawnMarauder(loc);
            case "MARKSMAN"   -> spawnMarksman(loc);
            case "HUNTER"     -> spawnHunter(loc);
            case "RAIDER"     -> spawnRaider(loc);
            case "DEFILER"    -> spawnDefiler(loc);
            case "ALCHEMIST"  -> spawnAlchemist(loc);
            case "REAPER"     -> spawnReaper(loc);
            case "WRAITH"     -> spawnWraith(loc);
            case "INCENDIARY" -> spawnIncendiary(loc);
            case "HOLLOW"     -> spawnHollow(loc);
            case "WARLORD"    -> spawnWarlord(loc);
            case "PSYCHOPATH" -> spawnPsychopath(loc);
            case "VOID"       -> spawnVoid(loc);
            case "CONJURER"   -> spawnConjurer(loc);
            default           -> null;
        };
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PHASE 1 — AWAKENING
    // ═══════════════════════════════════════════════════════════════════════

    private Zombie spawnVagabond(Location loc) {
        Zombie z = summon(loc, EntityType.ZOMBIE);
        name(z, "The Vagabond", NamedTextColor.GREEN, false);
        setHealth(z, 24.0);
        setSpeed(z, 0.26);
        setDamage(z, 4.0);

        EntityEquipment eq = z.getEquipment();
        eq.setHelmet(new ItemStack(Material.LEATHER_HELMET));
        eq.setItemInMainHand(new ItemStack(Material.WOODEN_SWORD));
        noDrops(eq);
        return z;
    }

    private Zombie spawnLurker(Location loc) {
        Zombie z = summon(loc, EntityType.ZOMBIE);
        name(z, "The Lurker", NamedTextColor.GREEN, true);
        setHealth(z, 28.0);
        setSpeed(z, 0.31);
        setDamage(z, 4.5);

        EntityEquipment eq = z.getEquipment();
        eq.setHelmet(new ItemStack(Material.LEATHER_HELMET));
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        colorLeather(chest, Color.fromRGB(30, 30, 30));
        eq.setChestplate(chest);
        noDrops(eq);

        addEffect(z, PotionEffectType.NIGHT_VISION, 0);
        return z;
    }

    private Zombie spawnLootGoblin(Location loc) {
        Zombie z = summon(loc, EntityType.ZOMBIE);
        name(z, "Loot Goblin", NamedTextColor.GREEN, false);
        setHealth(z, 20.0);
        setSpeed(z, 0.34);
        setDamage(z, 3.5);

        EntityEquipment eq = z.getEquipment();
        eq.setHelmet(new ItemStack(Material.GOLDEN_HELMET));
        eq.setItemInMainHand(new ItemStack(Material.STONE_SWORD));
        noDrops(eq);
        return z;
    }

    private Husk spawnVagrant(Location loc) {
        Husk h = summon(loc, EntityType.HUSK);
        name(h, "The Vagrant", NamedTextColor.GREEN, false);
        setHealth(h, 26.0);
        setSpeed(h, 0.27);
        setDamage(h, 4.5);
        return h;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PHASE 2 — INTENSIFYING
    // ═══════════════════════════════════════════════════════════════════════

    private Zombie spawnMarauder(Location loc) {
        Zombie z = summon(loc, EntityType.ZOMBIE);
        name(z, "The Marauder", NamedTextColor.YELLOW, true);
        setHealth(z, 45.0);
        setSpeed(z, 0.30);
        setDamage(z, 8.0);

        EntityEquipment eq = z.getEquipment();
        eq.setHelmet(enchantedItem(Material.IRON_HELMET,         "protection", 2));
        eq.setChestplate(enchantedItem(Material.IRON_CHESTPLATE, "protection", 2));
        eq.setLeggings(enchantedItem(Material.IRON_LEGGINGS,     "protection", 2));
        eq.setBoots(enchantedItem(Material.IRON_BOOTS,           "protection", 2));
        eq.setItemInMainHand(enchantedItem(Material.DIAMOND_SWORD, "sharpness", 3));
        noDrops(eq);

        addEffect(z, PotionEffectType.STRENGTH, 0);
        addEffect(z, PotionEffectType.RESISTANCE, 0);
        return z;
    }

    private Skeleton spawnMarksman(Location loc) {
        Skeleton s = summon(loc, EntityType.SKELETON);
        name(s, "The Marksman", NamedTextColor.YELLOW, false);
        setHealth(s, 38.0);
        setSpeed(s, 0.30);

        ItemStack bow = new ItemStack(Material.BOW);
        bow.addUnsafeEnchantment(ench("power"), 3);
        bow.addUnsafeEnchantment(ench("punch"), 1);
        bow.addUnsafeEnchantment(ench("infinity"), 1);

        EntityEquipment eq = s.getEquipment();
        eq.setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
        eq.setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
        eq.setItemInMainHand(bow);
        noDrops(eq);

        addEffect(s, PotionEffectType.SPEED, 0);
        return s;
    }

    private Zombie spawnHunter(Location loc) {
        Zombie z = summon(loc, EntityType.ZOMBIE);
        name(z, "The Hunter", NamedTextColor.YELLOW, true);
        setHealth(z, 40.0);
        setSpeed(z, 0.42);
        setDamage(z, 7.0);

        EntityEquipment eq = z.getEquipment();
        eq.setHelmet(new ItemStack(Material.IRON_HELMET));
        eq.setItemInMainHand(enchantedItem(Material.IRON_SWORD, "sharpness", 2));
        noDrops(eq);

        addEffect(z, PotionEffectType.SPEED, 1);
        return z;
    }

    private Pillager spawnRaider(Location loc) {
        Pillager p = summon(loc, EntityType.PILLAGER);
        name(p, "The Raider", NamedTextColor.YELLOW, true);
        setHealth(p, 42.0);
        setSpeed(p, 0.32);

        ItemStack crossbow = new ItemStack(Material.CROSSBOW);
        crossbow.addUnsafeEnchantment(ench("quick_charge"), 3);
        crossbow.addUnsafeEnchantment(ench("piercing"), 2);

        EntityEquipment eq = p.getEquipment();
        eq.setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
        eq.setItemInMainHand(crossbow);
        noDrops(eq);

        addEffect(p, PotionEffectType.SPEED, 0);
        return p;
    }

    private Blaze spawnIncendiary(Location loc) {
        Blaze b = summon(loc.clone().add(0, 2, 0), EntityType.BLAZE);
        name(b, "The Incendiary", NamedTextColor.YELLOW, true);
        setHealth(b, 45.0);
        setSpeed(b, 0.28);
        addEffect(b, PotionEffectType.FIRE_RESISTANCE, 0);
        addEffect(b, PotionEffectType.STRENGTH, 0);
        return b;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PHASE 3 — CRITICAL
    // ═══════════════════════════════════════════════════════════════════════

    private WitherSkeleton spawnDefiler(Location loc) {
        WitherSkeleton ws = summon(loc, EntityType.WITHER_SKELETON);
        name(ws, "The Defiler", NamedTextColor.GOLD, true);
        setHealth(ws, 65.0);
        setSpeed(ws, 0.30);
        setDamage(ws, 11.0);

        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        sword.addUnsafeEnchantment(ench("sharpness"), 4);
        sword.addUnsafeEnchantment(ench("fire_aspect"), 2);

        EntityEquipment eq = ws.getEquipment();
        eq.setHelmet(enchantedItem(Material.DIAMOND_HELMET,         "protection", 3));
        eq.setChestplate(enchantedItem(Material.DIAMOND_CHESTPLATE, "protection", 3));
        eq.setLeggings(enchantedItem(Material.DIAMOND_LEGGINGS,     "protection", 3));
        eq.setBoots(enchantedItem(Material.DIAMOND_BOOTS,           "protection", 3));
        eq.setItemInMainHand(sword);
        noDrops(eq);

        addEffect(ws, PotionEffectType.FIRE_RESISTANCE, 0);
        addEffect(ws, PotionEffectType.SPEED, 0);
        addEffect(ws, PotionEffectType.STRENGTH, 0);
        return ws;
    }

    private Witch spawnAlchemist(Location loc) {
        Witch w = summon(loc, EntityType.WITCH);
        name(w, "The Alchemist", NamedTextColor.GOLD, false);
        setHealth(w, 55.0);
        setSpeed(w, 0.30);

        addEffect(w, PotionEffectType.RESISTANCE, 1);
        addEffect(w, PotionEffectType.REGENERATION, 0);
        addEffect(w, PotionEffectType.SPEED, 0);
        return w;
    }

    private Vindicator spawnReaper(Location loc) {
        Vindicator v = summon(loc, EntityType.VINDICATOR);
        name(v, "The Reaper", NamedTextColor.GOLD, true);
        setHealth(v, 58.0);
        setSpeed(v, 0.34);
        setDamage(v, 12.0);

        ItemStack axe = new ItemStack(Material.DIAMOND_AXE);
        axe.addUnsafeEnchantment(ench("sharpness"), 4);

        EntityEquipment eq = v.getEquipment();
        eq.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        eq.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        eq.setItemInMainHand(axe);
        noDrops(eq);

        addEffect(v, PotionEffectType.SPEED, 0);
        addEffect(v, PotionEffectType.STRENGTH, 0);
        return v;
    }

    private Vex spawnWraith(Location loc) {
        Vex v = summon(loc.clone().add(0, 4, 0), EntityType.VEX);
        name(v, "The Wraith", NamedTextColor.GOLD, true);
        setHealth(v, 30.0);
        setDamage(v, 9.0);
        v.setCharging(true);

        EntityEquipment eq = v.getEquipment();
        eq.setItemInMainHand(enchantedItem(Material.IRON_SWORD, "sharpness", 3));
        noDrops(eq);

        addEffect(v, PotionEffectType.SPEED, 0);
        addEffect(v, PotionEffectType.INVISIBILITY, 0);
        return v;
    }

    private Ghast spawnHollow(Location loc) {
        Ghast g = summon(loc.clone().add(0, 8, 0), EntityType.GHAST);
        name(g, "The Hollow", NamedTextColor.GOLD, true);
        setHealth(g, 55.0);
        addEffect(g, PotionEffectType.FIRE_RESISTANCE, 0);
        return g;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PHASE 4 — COLLAPSE
    // ═══════════════════════════════════════════════════════════════════════

    private Zombie spawnWarlord(Location loc) {
        Zombie z = summon(loc, EntityType.ZOMBIE);
        name(z, "The Warlord", NamedTextColor.DARK_RED, true);
        setHealth(z, 80.0);
        setSpeed(z, 0.30);
        setDamage(z, 14.0);
        setAttribute(z, Attribute.ARMOR, 10.0);
        setAttribute(z, Attribute.ARMOR_TOUGHNESS, 4.0);

        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        sword.addUnsafeEnchantment(ench("sharpness"), 5);
        sword.addUnsafeEnchantment(ench("fire_aspect"), 2);

        EntityEquipment eq = z.getEquipment();
        eq.setHelmet(enchantedItem(Material.NETHERITE_HELMET,         "protection", 4));
        eq.setChestplate(enchantedItem(Material.NETHERITE_CHESTPLATE, "protection", 4));
        eq.setLeggings(enchantedItem(Material.NETHERITE_LEGGINGS,     "protection", 4));
        eq.setBoots(enchantedItem(Material.NETHERITE_BOOTS,           "protection", 4));
        eq.setItemInMainHand(sword);
        noDrops(eq);

        addEffect(z, PotionEffectType.REGENERATION, 1);
        addEffect(z, PotionEffectType.STRENGTH, 1);
        addEffect(z, PotionEffectType.RESISTANCE, 1);

        loc.getWorld().strikeLightningEffect(loc);
        return z;
    }

    private Vindicator spawnPsychopath(Location loc) {
        Vindicator v = summon(loc, EntityType.VINDICATOR);
        name(v, "The Psychopath", NamedTextColor.DARK_RED, true);
        setHealth(v, 60.0);
        setSpeed(v, 0.48);
        setDamage(v, 13.0);

        ItemStack axe = new ItemStack(Material.NETHERITE_AXE);
        axe.addUnsafeEnchantment(ench("sharpness"), 5);

        EntityEquipment eq = v.getEquipment();
        eq.setItemInMainHand(axe);
        noDrops(eq);

        addEffect(v, PotionEffectType.SPEED, 2);
        addEffect(v, PotionEffectType.INVISIBILITY, 0);
        addEffect(v, PotionEffectType.STRENGTH, 1);

        loc.getWorld().strikeLightningEffect(loc);
        return v;
    }

    private Wither spawnVoid(Location loc) {
        Wither w = summon(loc, EntityType.WITHER);
        name(w, "The Void", NamedTextColor.DARK_PURPLE, true);
        setHealth(w, 350.0);

        loc.getWorld().strikeLightningEffect(loc);
        loc.getWorld().strikeLightningEffect(loc.clone().add(2, 0, 0));
        loc.getWorld().strikeLightningEffect(loc.clone().add(-2, 0, 0));
        return w;
    }

    private Evoker spawnConjurer(Location loc) {
        Evoker e = summon(loc, EntityType.EVOKER);
        name(e, "The Conjurer", NamedTextColor.DARK_RED, true);
        setHealth(e, 65.0);
        setSpeed(e, 0.32);
        addEffect(e, PotionEffectType.RESISTANCE, 0);
        addEffect(e, PotionEffectType.SPEED, 0);
        loc.getWorld().strikeLightningEffect(loc);
        return e;
    }

    @SuppressWarnings("unchecked")
    private <T extends LivingEntity> T summon(Location loc, EntityType type) {
        LivingEntity e = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
        e.setRemoveWhenFarAway(false);
        return (T) e;
    }

    private void name(LivingEntity e, String text, NamedTextColor color, boolean bold) {
        Component comp = Component.text(text, color);
        if (bold) comp = comp.decorate(TextDecoration.BOLD);
        e.customName(comp);
        e.setCustomNameVisible(true);
    }

    private void setHealth(LivingEntity e, double hp) {
        setAttribute(e, Attribute.MAX_HEALTH, hp);
        e.setHealth(hp);
    }

    private void setSpeed(LivingEntity e, double speed) {
        setAttribute(e, Attribute.MOVEMENT_SPEED, speed);
    }

    private void setDamage(LivingEntity e, double dmg) {
        setAttribute(e, Attribute.ATTACK_DAMAGE, dmg);
    }

    private void setAttribute(LivingEntity e, Attribute attr, double value) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null) inst.setBaseValue(value);
    }

    private void addEffect(LivingEntity e, PotionEffectType type, int amplifier) {
        e.addPotionEffect(new PotionEffect(type, Integer.MAX_VALUE, amplifier, false, false));
    }

    private ItemStack enchantedItem(Material mat, String enchKey, int level) {
        ItemStack item = new ItemStack(mat);
        Enchantment enc = ench(enchKey);
        if (enc != null) item.addUnsafeEnchantment(enc, level);
        return item;
    }

    private Enchantment ench(String key) {
        return RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.ENCHANTMENT)
            .get(NamespacedKey.minecraft(key));
    }

    private void noDrops(EntityEquipment eq) {
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
    }

    private void colorLeather(ItemStack item, Color color) {
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            item.setItemMeta(meta);
        }
    }

    private Location randomLocationInZone(DeadZone zone) {
        Location center = zone.getCenter();
        double   radius = zone.getCurrentRadius();
        World    world  = center.getWorld();
        if (world == null) return null;

        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double dist  = random.nextDouble() * radius * 0.85;
            double x     = center.getX() + dist * Math.cos(angle);
            double z     = center.getZ() + dist * Math.sin(angle);
            int    y     = world.getHighestBlockYAt((int) x, (int) z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Location candidate = new Location(world, x, y + 1, z);
            if (candidate.getBlock().isPassable()) return candidate;
        }
        return center.clone().add(0, 1, 0);
    }

    public void clearZoneMobs(DeadZone zone) {
        for (UUID uuid : zone.getZoneMobs()) {
            Entity e = plugin.getServer().getEntity(uuid);
            if (e != null && e.isValid()) e.remove();
        }
        new ArrayList<>(zone.getZoneMobs()).forEach(zone::untrackMob);
    }

    public boolean isZoneMob(Entity entity) {
        return entity.getPersistentDataContainer().has(ZONE_MOB_KEY, PersistentDataType.BYTE);
    }
}
