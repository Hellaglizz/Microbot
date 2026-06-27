package net.runelite.client.plugins.microbot.doom.handlers;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.doom.DoomConfig;
import net.runelite.client.plugins.microbot.doom.data.DoomIds;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.ScheduledFuture;

/**
 * EXPERIMENTAL resupply + travel routine, encoded from a SINGLE captured bank
 * trip (session-20260618). It has NOT been live-tested. It only runs when
 * `bankingEnabled` is on; otherwise the script banks the loot and stops for a
 * manual resupply.
 *
 * Captured lap:
 *   Teleport-to-house tablet -> Drink Ornate pool -> Jewellery box "Castle Wars"
 *   -> Castle Wars bank chest (reload InventorySetups) -> "Kastori" teleport ->
 *   Tonali Teleporter -> entrance -> lobby -> Jump-over the Gap into delve 1.
 *
 * Each step advances only when the precondition for the NEXT step is detectable,
 * so a missed click just retries rather than skipping ahead. Returns true once a
 * full lap completes (back in the lobby and re-entered).
 *
 * This is the brittle part of the project — region-specific travel from one
 * recording. Expect to iterate on it live, step by step.
 */
public class BankRoutine
{
    private enum Step { TELE_POH, RESTORE_POOL, JEWELLERY_CW, BANK_RESTOCK, RETURN_TELE, TO_LOBBY, ENTER_DELVE, DONE }

    private Step step = Step.TELE_POH;

    /** @return true when a full resupply lap is complete and we're back in delve 1. */
    public boolean run(DoomConfig cfg, ScheduledFuture<?> scheduler)
    {
        switch (step)
        {
            case TELE_POH:
                // Use the POH teleport tablet; advance once the ornate pool is in range.
                if (Rs2GameObject.exists(DoomIds.OBJ_ORNATE_POOL)) { step = Step.RESTORE_POOL; break; }
                Rs2Inventory.interact("Teleport to house", "Break");
                break;

            case RESTORE_POOL:
                if (Rs2GameObject.interact(DoomIds.OBJ_ORNATE_POOL, "Drink", 12)) step = Step.JEWELLERY_CW;
                break;

            case JEWELLERY_CW:
                // Jewellery box -> Castle Wars; advance when the CW bank chest is reachable.
                if (Rs2GameObject.exists(DoomIds.OBJ_CW_BANK_CHEST)) { step = Step.BANK_RESTOCK; break; }
                Rs2GameObject.interact("Fancy Jewellery Box", "Castle Wars");
                break;

            case BANK_RESTOCK:
                if (!Rs2Bank.isOpen())
                {
                    Rs2GameObject.interact(DoomIds.OBJ_CW_BANK_CHEST, "Use", 8);
                    break;
                }
                // Reload the configured loadout (potions, food, runes, gear).
                Rs2InventorySetup setup = new Rs2InventorySetup(cfg.inventorySetup(), scheduler);
                boolean inv = setup.loadInventory();
                boolean eq  = setup.loadEquipment();
                if (inv && eq && setup.doesInventoryMatch())
                {
                    Rs2Bank.closeBank();
                    step = Step.RETURN_TELE;
                }
                break;

            case RETURN_TELE:
                // Captured return: "Kastori" teleport item, then the Tonali
                // Teleporter NPC drops you near the Doom entrance.
                if (Rs2Player.getWorldLocation().getRegionID() == DoomIds.LOBBY_REGION) { step = Step.ENTER_DELVE; break; }
                if (DoomQuery.anyPresent(DoomIds.NPC_TONALI_TELE))
                {
                    var tele = Microbot.getRs2NpcCache().query().withId(DoomIds.NPC_TONALI_TELE).nearest();
                    if (tele != null) tele.interact("Teleport");
                    step = Step.TO_LOBBY;
                    break;
                }
                Rs2Inventory.interact("Kastori", "Break");
                break;

            case TO_LOBBY:
                // From the Doom entrance area down into the lobby (region 5269).
                if (Rs2Player.getWorldLocation().getRegionID() == DoomIds.LOBBY_REGION) { step = Step.ENTER_DELVE; break; }
                if (!Rs2GameObject.interact(DoomIds.OBJ_DOOM_ENTRANCE, "Pass-through", 20))
                {
                    // fall back to walking toward the entrance object
                    Rs2GameObject.interact(DoomIds.OBJ_DOOM_ENTRANCE, "Pass-through", 50);
                }
                break;

            case ENTER_DELVE:
                // Jump the Gap to drop into delve 1.
                if (Rs2GameObject.interact(DoomIds.OBJ_GAP_ENTER, "Jump-over", 10)) step = Step.DONE;
                break;

            case DONE:
                step = Step.TELE_POH; // reset for the next lap
                return true;
        }
        return false;
    }

    public void reset() { step = Step.TELE_POH; }
}
