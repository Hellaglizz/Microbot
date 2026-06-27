package net.runelite.client.plugins.microbot.doom.handlers;

import net.runelite.api.MagicAction;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;

/**
 * Death Charge (Arceuus): cast on cooldown to refund special-attack energy on the
 * next boss kill. Verified in the capture — each cast produces "Upon the death of
 * your next foe, some of your special attack energy will be restored", and spec
 * jumps when the delve boss dies.
 *
 * Cooldown is 60s (100 ticks); we self-track it because canCast() only checks
 * runes/level/spellbook, not the active cooldown.
 */
public class SpellHandler
{
    private static final int DEATH_CHARGE_COOLDOWN_TICKS = 100;
    private int lastDeathChargeTick = -DEATH_CHARGE_COOLDOWN_TICKS;

    /**
     * Cast Death Charge if enabled, off cooldown, spec isn't already full, and we
     * have the runes. Returns true if cast this tick.
     */
    public boolean maybeDeathCharge(int specPercent, boolean enabled)
    {
        if (!enabled) return false;
        if (specPercent >= 100) return false;

        int now = Microbot.getClient().getTickCount();
        if (now - lastDeathChargeTick < DEATH_CHARGE_COOLDOWN_TICKS) return false;
        if (!Rs2Magic.canCast(MagicAction.DEATH_CHARGE)) return false;

        if (Rs2Magic.cast(MagicAction.DEATH_CHARGE))
        {
            lastDeathChargeTick = now;
            return true;
        }
        return false;
    }

    public void reset()
    {
        lastDeathChargeTick = -DEATH_CHARGE_COOLDOWN_TICKS;
    }
}
