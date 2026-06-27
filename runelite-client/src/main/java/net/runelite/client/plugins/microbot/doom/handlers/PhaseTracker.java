package net.runelite.client.plugins.microbot.doom.handlers;

import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.doom.data.DoomIds;
import net.runelite.client.plugins.microbot.doom.enums.DoomState;

/**
 * Derives the fight state from the live scene each tick via the queryable cache.
 * Nothing here is a hard-coded step counter — state is read from boss form /
 * animation / projectiles / scene NPCs so the loop recovers if the boss goes
 * off-script.
 *
 * Detection priority (highest-danger first):
 *   SHOCKWAVE > MELEE_PUNISH > ROCK_THROW > SHIELD_PHASE > CAR_PHASE > STANDARD
 */
public class PhaseTracker
{
    private int delveLevel = 1;

    public int getDelveLevel() { return delveLevel; }
    public void setDelveLevel(int d) { delveLevel = d; }

    public Rs2NpcModel findDoom()
    {
        return DoomQuery.doom();
    }

    public boolean isShielded(Rs2NpcModel doom)
    {
        return doom != null && doom.getId() == DoomIds.DOOM_SHIELDED;
    }

    public boolean isBurrowed(Rs2NpcModel doom)
    {
        return doom != null && doom.getId() == DoomIds.DOOM_BURROWED;
    }

    /** Volatile earth is an NPC (14714); its presence == the shockwave telegraph. */
    public boolean isShockwaveActive()
    {
        return DoomQuery.anyPresent(DoomIds.NPC_VOLATILE_EARTH);
    }

    /** Looming rock shadows (gfx 2380) present == a rock throw is mid-flight. */
    public boolean isRockInAir()
    {
        return DoomIds.GFX_ROCK_SHADOW != -1
            && !RockThrowHandler.graphicsOfType(DoomIds.GFX_ROCK_SHADOW).isEmpty();
    }

    /**
     * Melee charge: signalled by animation 12409, which the capture showed fires
     * ONLY during a melee charge (14/14 occurrences, all immediately before a
     * halberd-spec punish). The combined Magic+Missiles overhead is NOT exposed
     * via getOverheadIcon() — no overhead events fired across the whole run —
     * so animation is the reliable signal, not the overhead.
     */
    public boolean isMeleeCharging(Rs2NpcModel doom)
    {
        return doom != null && doom.getAnimation() == DoomIds.ANIM_MELEE_CHARGE;
    }

    public DoomState resolve()
    {
        Rs2NpcModel doom = findDoom();
        if (doom == null) return DoomState.IDLE;

        if (isShockwaveActive())   return DoomState.SHOCKWAVE;
        if (isMeleeCharging(doom)) return DoomState.MELEE_PUNISH;
        if (isRockInAir())         return DoomState.ROCK_THROW;
        if (isShielded(doom))      return DoomState.SHIELD_PHASE;
        if (isBurrowed(doom))      return DoomState.CAR_PHASE;
        return DoomState.STANDARD;
    }
}
