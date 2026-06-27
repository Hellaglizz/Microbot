package net.runelite.client.plugins.microbot.corruptedgauntlet.handlers;

import net.runelite.api.Projectile;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.corruptedgauntlet.CorruptedGauntletConfig;
import net.runelite.client.plugins.microbot.corruptedgauntlet.data.GauntletIds;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.List;

/**
 * Corrupted Hunllef combat. Two independent signals drive everything (verified
 * across 3 traces + wiki):
 *
 *   WEAPON  <- the style the boss is PROTECTING, read from its NPC id
 *             (9035 melee / 9036 ranged / 9037 magic). Attack the style it is NOT
 *             protecting. magic+ranged build: protect-ranged -> use staff (magic);
 *             otherwise -> use bow (ranged).
 *   PRAYER  <- the style the boss is ATTACKING with, read from its projectile
 *             (1708 magic / 1712 ranged). Pray the matching protection.
 *
 * Hazard priority (do the first that applies): Tornados > Tiles > Prayer > Stomp.
 * Tornadoes (NPC 9039) chase at 1 tile/tick for 20t; we out-run them to a safe
 * tile. Damage floor tiles (36047/36048) deal 10-20/tick. Never stand under the
 * boss on its attack tick (~68 stomp).
 */
public class BossHandler
{
    private static Rs2PrayerEnum currentProtection = Rs2PrayerEnum.PROTECT_RANGE; // boss starts ranged

    /**
     * A live (non-dead) Hunllef exists in the scene. NOTE: the Hunllef is loaded
     * the ENTIRE instance (it sits in its arena while you prep), so this is true
     * during prep too — it must NOT be used to decide we're fighting. Use it only,
     * combined with the "arena entered" flag, to detect the kill (boss gone).
     */
    public static boolean bossPresent()
    {
        return hunllef() != null;
    }

    /** Are we actually next to the Hunllef (in its arena)? Prep keeps you 14+ away. */
    public static boolean nearBoss(int maxDist)
    {
        Rs2NpcModel b = hunllef();
        WorldPoint me = Rs2Player.getWorldLocation();
        if (b == null || me == null || b.getWorldArea() == null) return false;
        return b.getWorldArea().distanceTo(me) <= maxDist;
    }

    public static void reset()
    {
        currentProtection = Rs2PrayerEnum.PROTECT_RANGE;
    }

    public static void fight(CorruptedGauntletConfig c)
    {
        Rs2NpcModel boss = hunllef();
        if (boss == null) return;

        // ---- PRAYER: match the incoming attack style (highest-frequency action). ----
        Rs2PrayerEnum incoming = incomingProtection();
        if (incoming != null) currentProtection = incoming;
        ensureOnlyProtection(currentProtection);

        // ---- WEAPON: attack the style the boss isn't protecting. ----
        String protects = GauntletIds.hunllefProtection(boss.getId());
        boolean useStaff = "ranged".equals(protects);   // protecting ranged -> use magic
        equipWeapon(useStaff);
        if (c.useOffensivePrayers()) ensureOffensive(useStaff);

        // ---- HAZARDS (Tornados > Tiles > Stomp): dodge before attacking. ----
        if (dodgeIfNeeded(boss))
            return;

        // ---- SURVIVAL: eat / sip. ----
        if (Rs2Player.getHealthPercentage() <= c.eatAtPercent())
        {
            if (Rs2Inventory.interact(GauntletIds.ITEM_FOOD, "Eat")) return;
        }

        // ---- DPS: attack the boss (never end under it — Rs2Npc.attack paths to range). ----
        if (!Rs2Player.isInteracting())
            Rs2Npc.attack(boss);
    }

    // ------------------------------------------------------------------
    // Hazard avoidance
    // ------------------------------------------------------------------
    private static boolean dodgeIfNeeded(Rs2NpcModel boss)
    {
        WorldPoint me = Rs2Player.getWorldLocation();
        if (me == null) return false;

        List<WorldPoint> danger = new ArrayList<>();
        // Tornadoes: their tile + the tile one step toward us (where they'll be).
        for (Rs2NpcModel t : Rs2Npc.getNpcs(GauntletIds.HUNLLEF_TORNADO).collect(java.util.stream.Collectors.toList()))
        {
            WorldPoint tp = t.getWorldLocation();
            if (tp == null) continue;
            danger.add(tp);
            danger.add(stepToward(tp, me));
        }
        // Damage floor tiles.
        for (int id : new int[]{ GauntletIds.TILE_HIT, GauntletIds.TILE_WARNING })
        {
            net.runelite.api.GameObject go = net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject.getGameObject(id);
            if (go != null && go.getWorldLocation() != null) danger.add(go.getWorldLocation());
        }
        // Never stand under the boss (stomp).
        if (boss.getWorldArea() != null)
            for (WorldPoint wp : boss.getWorldArea().toWorldPointList())
                danger.add(wp);

        boolean threatened = danger.stream().anyMatch(d -> d.distanceTo(me) <= 1);
        if (!threatened) return false;

        // Pick the nearby walkable tile that maximises distance to the nearest hazard.
        WorldPoint best = null; int bestScore = -1;
        for (WorldPoint cand : Rs2Tile.getWalkableTilesAroundPlayer(5))
        {
            if (danger.contains(cand)) continue;
            int score = danger.stream().mapToInt(d -> d.distanceTo(cand)).min().orElse(99);
            if (score > bestScore) { bestScore = score; best = cand; }
        }
        if (best != null)
        {
            Microbot.status = "[CG] dodging hazard";
            Rs2Walker.walkFastCanvas(best);
            return true;
        }
        return false;
    }

    private static WorldPoint stepToward(WorldPoint from, WorldPoint to)
    {
        int dx = Integer.signum(to.getX() - from.getX());
        int dy = Integer.signum(to.getY() - from.getY());
        return new WorldPoint(from.getX() + dx, from.getY() + dy, from.getPlane());
    }

    // ------------------------------------------------------------------
    // Prayer / weapon
    // ------------------------------------------------------------------
    /** Scan in-flight Hunllef projectiles for the current attack style. */
    private static Rs2PrayerEnum incomingProtection()
    {
        Boolean magic = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            for (Projectile p : Microbot.getClient().getProjectiles())
            {
                if (p.getId() == GauntletIds.PROJ_MAGIC || p.getId() == GauntletIds.PROJ_MAGIC_NORMAL) return Boolean.TRUE;
                if (p.getId() == GauntletIds.PROJ_RANGED || p.getId() == GauntletIds.PROJ_RANGED_NORMAL) return Boolean.FALSE;
            }
            return null;
        }).orElse(null);
        if (magic == null) return null;
        return magic ? Rs2PrayerEnum.PROTECT_MAGIC : Rs2PrayerEnum.PROTECT_RANGE;
    }

    private static void ensureOnlyProtection(Rs2PrayerEnum want)
    {
        if (!Rs2Prayer.isPrayerActive(want)) Rs2Prayer.toggle(want, true);
        for (Rs2PrayerEnum other : new Rs2PrayerEnum[]{
            Rs2PrayerEnum.PROTECT_MAGIC, Rs2PrayerEnum.PROTECT_RANGE, Rs2PrayerEnum.PROTECT_MELEE })
            if (other != want && Rs2Prayer.isPrayerActive(other)) Rs2Prayer.toggle(other, false);
    }

    private static void ensureOffensive(boolean staff)
    {
        Rs2PrayerEnum want = staff ? Rs2PrayerEnum.MYSTIC_MIGHT : Rs2PrayerEnum.EAGLE_EYE;
        Rs2PrayerEnum drop = staff ? Rs2PrayerEnum.EAGLE_EYE : Rs2PrayerEnum.MYSTIC_MIGHT;
        if (Rs2Prayer.isPrayerActive(drop)) Rs2Prayer.toggle(drop, false);
        if (!Rs2Prayer.isPrayerActive(want)) Rs2Prayer.toggle(want, true);
    }

    private static void equipWeapon(boolean staff)
    {
        String base = staff ? GauntletIds.ITEM_STAFF : GauntletIds.ITEM_BOW;
        if (Rs2Equipment.isWearing(base, false)) return;
        Rs2ItemModel item = Rs2Inventory.get(base, false);
        if (item != null) Rs2Inventory.interact(item, "Wield");
    }

    // ------------------------------------------------------------------
    private static Rs2NpcModel hunllef()
    {
        for (int id : GauntletIds.HUNLLEF_ALL)
        {
            if (id == GauntletIds.HUNLLEF_DEATH) continue;
            Rs2NpcModel n = Rs2Npc.getNpc(id);
            if (n != null) return n;
        }
        return null;
    }

    public static boolean dead()
    {
        return Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) <= 0;
    }
}
