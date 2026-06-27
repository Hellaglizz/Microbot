package net.runelite.client.plugins.microbot.doom.handlers;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.doom.data.DoomIds;

import java.util.List;

/**
 * Thin wrapper over the queryable NPC cache (Rs2NpcCache). Centralises the Doom
 * lookups so the handlers stay clean and there's one place to tune. The cache
 * refreshes per game tick, so these reads are cheap and consistent within a tick.
 */
public final class DoomQuery
{
    private DoomQuery() {}

    /** The Doom in any of its three forms, nearest, or null if not present. */
    public static Rs2NpcModel doom()
    {
        return Microbot.getRs2NpcCache().query()
            .withIds(DoomIds.DOOM_NORMAL, DoomIds.DOOM_SHIELDED, DoomIds.DOOM_BURROWED)
            .nearest();
    }

    /** All NPCs matching any of the given ids (-1 ids are ignored by withIds). */
    public static List<Rs2NpcModel> withIds(int... ids)
    {
        return Microbot.getRs2NpcCache().query().withIds(ids).toList();
    }

    /** Nearest NPC matching any of the given ids, or null. */
    public static Rs2NpcModel nearest(int... ids)
    {
        return Microbot.getRs2NpcCache().query().withIds(ids).nearest();
    }

    /** Whether any NPC of this id exists in the scene. */
    public static boolean anyPresent(int id)
    {
        if (id == -1) return false;
        return !Microbot.getRs2NpcCache().query().withId(id).toList().isEmpty();
    }
}
