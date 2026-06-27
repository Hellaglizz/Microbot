package net.runelite.client.plugins.microbot.doom.handlers;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.doom.data.DoomIds;
import net.runelite.client.plugins.microbot.doom.data.DoomTiles;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Comparator;
import java.util.List;

/**
 * Demonic larvae ("grubs"). 2 HP, only one-shot by demonbane / multi-hit / Eye
 * of Ayak. Colour == style at delve 4+ (red melee, green ranged, blue magic).
 * Melee larvae only appear in the shield phase. They spawn from the western
 * hemisphere and march toward the Doom.
 *
 * Priority: MELEE first (they reach you and hit hardest), then the larva closest
 * to the boss (about to be absorbed) — but never at the cost of the D1 line.
 */
public class LarvaHandler
{
    public enum Style { MELEE, RANGED, MAGIC, NEUTRAL, UNKNOWN }

    public Style styleOf(Rs2NpcModel larva)
    {
        int id = larva.getId();
        if (id == DoomIds.LARVA_MELEE) return Style.MELEE;
        if (id == DoomIds.LARVA_RANGED || id == DoomIds.LARVA_GIANT_RANGED) return Style.RANGED;
        if (id == DoomIds.LARVA_MAGIC  || id == DoomIds.LARVA_GIANT_MAGIC)  return Style.MAGIC;
        if (id == DoomIds.LARVA_NEUTRAL) return Style.NEUTRAL;
        return Style.UNKNOWN;
    }

    public boolean isGiant(Rs2NpcModel larva)
    {
        return larva.getId() == DoomIds.LARVA_GIANT_RANGED
            || larva.getId() == DoomIds.LARVA_GIANT_MAGIC;
    }

    public List<Rs2NpcModel> all()
    {
        return DoomQuery.withIds(
            DoomIds.LARVA_NEUTRAL, DoomIds.LARVA_RANGED, DoomIds.LARVA_MAGIC,
            DoomIds.LARVA_MELEE, DoomIds.LARVA_GIANT_RANGED, DoomIds.LARVA_GIANT_MAGIC);
    }

    public boolean comingFromWest()
    {
        for (Rs2NpcModel l : all())
            if (DoomTiles.isWest(l.getWorldLocation())) return true;
        return false;
    }

    /** The grub to attack now: melee first, else closest to the boss. */
    public Rs2NpcModel nextTarget(Rs2NpcModel doom)
    {
        List<Rs2NpcModel> larvae = all();
        if (larvae.isEmpty() || doom == null) return null;

        final WorldPoint me = Rs2Player.getWorldLocation();
        Rs2NpcModel bestMelee = null;
        int bestMeleeDist = Integer.MAX_VALUE;
        for (Rs2NpcModel l : larvae)
        {
            if (styleOf(l) != Style.MELEE) continue;
            int d = l.getWorldLocation().distanceTo(me);
            if (d < bestMeleeDist) { bestMeleeDist = d; bestMelee = l; }
        }
        if (bestMelee != null) return bestMelee;

        final WorldPoint bossLoc = doom.getWorldLocation();
        larvae.sort(Comparator.comparingInt(l -> l.getWorldLocation().distanceTo(bossLoc)));
        return larvae.get(0);
    }

    /** Weapon tag for a larva style (matches GearHandler loadout names). */
    public String weaponFor(Style style)
    {
        switch (style)
        {
            case RANGED:  return "bow";
            case MELEE:   return "darklight";
            case MAGIC:   return "slayerstaff";
            default:      return "bow";
        }
    }
}
