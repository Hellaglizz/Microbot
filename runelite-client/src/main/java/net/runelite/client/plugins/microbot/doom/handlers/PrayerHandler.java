package net.runelite.client.plugins.microbot.doom.handlers;

import net.runelite.api.Projectile;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.doom.data.DoomIds;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

/**
 * Prayer flicking. The Doom's standard attack and its rock-throw follow-up are
 * both projectile-telegraphed (blue=magic, green=ranged, red=melee), and the
 * damage is calculated on impact, so reacting to the projectile in flight is
 * enough. We pick the protection prayer matching the *latest* incoming
 * projectile, defaulting to nothing when the air is clear.
 *
 * Rock-throw nuance handled by the caller: after the rock explodes the player's
 * prayers are force-disabled for a tick, so the caller re-asserts the prayer
 * AFTER impact (and on delve 8 only after the SECOND rock explodes).
 */
public class PrayerHandler
{
    public enum Style { MAGIC, RANGED, MELEE, NONE }

    /** Map a Doom projectile id to the style it represents. */
    public Style styleOf(int projectileId)
    {
        if (projectileId == DoomIds.PROJ_MAGIC || projectileId == DoomIds.PROJ_ROCK_MAGIC)
            return Style.MAGIC;
        if (projectileId == DoomIds.PROJ_RANGED || projectileId == DoomIds.PROJ_ROCK_RANGED)
            return Style.RANGED;
        if (projectileId == DoomIds.PROJ_MELEE)
            return Style.MELEE;
        return Style.NONE;
    }

    /** Find the most recently launched relevant projectile and return its style. */
    public Style incomingStyle()
    {
        Style result = Style.NONE;
        int newest = Integer.MAX_VALUE; // smaller remaining cycles == lands sooner
        for (Projectile p : Microbot.getClient().getProjectiles())
        {
            Style s = styleOf(p.getId());
            if (s == Style.NONE) continue;
            int remaining = p.getRemainingCycles();
            if (remaining < newest)
            {
                newest = remaining;
                result = s;
            }
        }
        return result;
    }

    /** Activate the protection prayer matching the incoming style. Returns the style chosen. */
    public Style protectAgainstIncoming()
    {
        Style s = incomingStyle();
        switch (s)
        {
            case MAGIC:  ensure(Rs2PrayerEnum.PROTECT_MAGIC);  break;
            case RANGED: ensure(Rs2PrayerEnum.PROTECT_RANGE);  break;
            case MELEE:  ensure(Rs2PrayerEnum.PROTECT_MELEE);  break;
            default: /* nothing incoming — leave prayers as-is */ break;
        }
        return s;
    }

    /** Turn on the offensive ranged prayer (Rigour preferred, Eagle Eye/Deadeye fallback). */
    public void offensiveRanged()
    {
        if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.RIGOUR))
            Rs2Prayer.toggle(Rs2PrayerEnum.RIGOUR, true);
    }

    /** Drop all overheads (call before stepping into the next wave / burrow prompt). */
    public void clearOverheads()
    {
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, false);
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, false);
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
    }

    private void ensure(Rs2PrayerEnum prayer)
    {
        // Single overhead at a time: toggle off the other two implicitly by only
        // asserting the one we want. Rs2Prayer.toggle is idempotent.
        if (!Rs2Prayer.isPrayerActive(prayer))
        {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, prayer == Rs2PrayerEnum.PROTECT_MAGIC);
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, prayer == Rs2PrayerEnum.PROTECT_RANGE);
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, prayer == Rs2PrayerEnum.PROTECT_MELEE);
        }
    }
}
