package net.runelite.client.plugins.microbot.doom.handlers;

import net.runelite.client.plugins.microbot.doom.DoomConfig;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/**
 * Session-level decisions made at the burrow hole between delves:
 *   - if a unique dropped  -> BANK (claim it, resupply, restart from delve 1)
 *   - if a hard delve cap is set and reached -> BANK
 *   - if supplies are too low for another delve -> BANK
 *   - otherwise -> DESCEND
 *
 * The unique flag is set by DoomPlugin's chat listener. The supply check is
 * presence-based (configurable): if any required consumable is gone, stop pushing.
 */
public class SessionManager
{
    public enum Decision { DESCEND, BANK }

    private volatile boolean uniqueDropped = false;

    public void flagUnique()  { uniqueDropped = true; }
    public void clearUnique() { uniqueDropped = false; }
    public boolean uniqueDropped() { return uniqueDropped; }

    /** Presence check for the consumables needed to safely push another delve. */
    public boolean enoughSupplies(DoomConfig cfg)
    {
        boolean ok = true;
        if (cfg.requireFood())      ok &= has(cfg.foodName());
        if (cfg.requirePrayer())    ok &= has(cfg.prayerPotionName());
        if (cfg.requireBrew())      ok &= has(cfg.emergencyBrewName());
        if (cfg.requireAntiVenom()) ok &= has(cfg.antiVenomName());
        return ok;
    }

    public Decision decide(DoomConfig cfg, int delveLevel)
    {
        if (uniqueDropped) return Decision.BANK;
        if (cfg.maxDelve() > 0 && delveLevel >= cfg.maxDelve()) return Decision.BANK;
        if (cfg.bankOnLowSupplies() && !enoughSupplies(cfg)) return Decision.BANK;
        return Decision.DESCEND;
    }

    /** Partial-name presence (handles dosed potions like "Prayer potion(3)"). */
    private boolean has(String baseName)
    {
        if (baseName == null || baseName.isEmpty()) return true;
        return Rs2Inventory.hasItem(baseName);
    }
}
