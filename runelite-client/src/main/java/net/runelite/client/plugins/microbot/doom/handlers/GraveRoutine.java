package net.runelite.client.plugins.microbot.doom.handlers;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.doom.DoomConfig;
import net.runelite.client.plugins.microbot.doom.data.DoomIds;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Post-death recovery: after dying in the delve a "Grave" (9856) holds your
 * items. Loot it, then jump back into delve 1 via the Gap if supplies allow;
 * otherwise hand control back so the caller can bank / stop.
 *
 * EXPERIMENTAL — no death-recovery capture exists yet, so the grave's loot
 * action and the respawn location are best guesses. The flow is defensive (it
 * no-ops when there's no grave), but expect to tune the loot action / re-entry
 * once a death is recorded with the logger running.
 */
public class GraveRoutine
{
    private enum Phase { WATCH, LOOTED }
    private Phase phase = Phase.WATCH;

    /** @return true if a recovery action was taken this tick (caller should stop other logic). */
    public boolean recover(DoomConfig cfg, SessionManager session)
    {
        Rs2NpcModel grave = Microbot.getRs2NpcCache().query().withId(DoomIds.NPC_GRAVE).nearest();

        if (grave != null)
        {
            // Grave present -> loot it (action unverified: try the likely ones).
            if (!grave.click("Loot") && !grave.click("Take") && !grave.click("Open"))
                grave.click("Read");
            phase = Phase.LOOTED;
            return true;
        }

        if (phase != Phase.LOOTED) return false; // never died this session — nothing to do

        // Grave is gone -> we've looted. Re-enter if we have supplies, else stop.
        phase = Phase.WATCH;
        if (!session.enoughSupplies(cfg))
        {
            Microbot.log("Recovered from death but supplies are low — stopping (bank/resupply manually).");
            return false;
        }

        if (Rs2GameObject.exists(DoomIds.OBJ_GAP_ENTER))
        {
            Microbot.log("Recovered from death — re-entering delve 1.");
            Rs2GameObject.interact(DoomIds.OBJ_GAP_ENTER, "Jump-over", 10);
            return true;
        }
        return false;
    }

    public void reset() { phase = Phase.WATCH; }
}
