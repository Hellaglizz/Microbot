package net.runelite.client.plugins.microbot.corruptedgauntlet.handlers;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.corruptedgauntlet.CorruptedGauntletConfig;
import net.runelite.client.plugins.microbot.corruptedgauntlet.data.GauntletIds;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Prep phase: gather resources to the configured quotas (looting drops, mining/
 * chopping/fishing/picking, and killing creatures for shards), then craft gear,
 * cook food and brew potions at the base.
 *
 * Gathering is well-grounded in the traces (object/ground-item names verified).
 * Crafting/cooking/potions go through the Singing Bowl "Make" interface (group
 * 270) and the Range; the exact widget components are sprite-based and shift with
 * the available recipe list, so those steps are best-effort and marked TODO for
 * in-game confirmation of the component ids.
 */
public class PrepHandler
{
    /** Pick up any of our resource drops lying nearby so nothing is wasted. */
    private static final String[] LOOT = {
        GauntletIds.ITEM_SHARDS, GauntletIds.ITEM_ORE, GauntletIds.ITEM_BARK,
        GauntletIds.ITEM_LINUM, GauntletIds.ITEM_GRYM, GauntletIds.ITEM_RAW_FOOD,
        "Weapon frame", "Corrupted bowstring", "Corrupted orb"
    };

    // ------------------------------------------------------------------
    // Quota checks
    // ------------------------------------------------------------------
    public static boolean gatherDone(CorruptedGauntletConfig c)
    {
        return shards() >= c.targetShards()
            && Rs2Inventory.count(GauntletIds.ITEM_ORE)   >= c.targetOre()
            && Rs2Inventory.count(GauntletIds.ITEM_BARK)  >= c.targetOre()
            && Rs2Inventory.count(GauntletIds.ITEM_LINUM) >= c.targetOre()
            && Rs2Inventory.count(GauntletIds.ITEM_GRYM)  >= c.targetGrym()
            && rawAndCookedFood() >= c.targetFood();
    }

    public static boolean ready(CorruptedGauntletConfig c)
    {
        // Ready to fight: a weapon + some food crafted/cooked.
        return Rs2Inventory.hasItem(GauntletIds.ITEM_BOW)
            && Rs2Inventory.count(GauntletIds.ITEM_FOOD) >= Math.min(8, c.targetFood())
            && GauntletNav.hasSceptre();
    }

    private static int shards() { return Rs2Inventory.count(GauntletIds.ITEM_SHARDS); }
    private static int rawAndCookedFood()
    {
        return Rs2Inventory.count(GauntletIds.ITEM_FOOD)
             + Rs2Inventory.count(GauntletIds.ITEM_RAW_FOOD);
    }

    // ------------------------------------------------------------------
    // Gathering loop — one decision per call
    // ------------------------------------------------------------------
    public static void gather(CorruptedGauntletConfig c)
    {
        // 1) Grab any of our drops on the floor first.
        for (String name : LOOT)
        {
            if (Rs2GroundItem.exists(name, 12) && Rs2GroundItem.loot(name, 12))
            {
                sleepUntil(() -> !Rs2Player.isMoving(), 2000);
                return;
            }
        }

        // 2) Short on shards? Kill a nearby corrupted creature (prefer demi-bosses
        //    for the tier-3 upgrades; trash drops shards too). Demi-bosses also
        //    need killing regardless for the upgrade seeds.
        if (shards() < c.targetShards())
        {
            Rs2NpcModel target = nearestCreature();
            if (target != null)
            {
                if (!Rs2Player.isInteracting()) Rs2Npc.attack(target);
                return;
            }
        }

        // 3) Gather the most-needed resource that's present in this room.
        if (Rs2Inventory.count(GauntletIds.ITEM_ORE) < c.targetOre()
            && GauntletNav.isPresent(GauntletIds.OBJN_DEPOSIT))
        { GauntletNav.interactObject(GauntletIds.OBJN_DEPOSIT, "Mine"); waitSkill(); return; }

        if (Rs2Inventory.count(GauntletIds.ITEM_BARK) < c.targetOre()
            && GauntletNav.isPresent(GauntletIds.OBJN_PHREN))
        { GauntletNav.interactObject(GauntletIds.OBJN_PHREN, "Chop"); waitSkill(); return; }

        if (Rs2Inventory.count(GauntletIds.ITEM_LINUM) < c.targetOre()
            && GauntletNav.isPresent(GauntletIds.OBJN_LINUM))
        { GauntletNav.interactObject(GauntletIds.OBJN_LINUM, "Pick"); waitSkill(); return; }

        if (Rs2Inventory.count(GauntletIds.ITEM_GRYM) < c.targetGrym()
            && GauntletNav.isPresent(GauntletIds.OBJN_GRYM))
        { GauntletNav.interactObject(GauntletIds.OBJN_GRYM, "Pick"); waitSkill(); return; }

        if (rawAndCookedFood() < c.targetFood()
            && GauntletNav.isPresent(GauntletIds.OBJN_FISHING))
        { GauntletNav.interactObject(GauntletIds.OBJN_FISHING, "Fish"); waitSkill(); return; }

        // 4) Nothing needed here — open a new room and re-scan next tick.
        Microbot.status = "[CG] exploring for resources";
        GauntletNav.exploreStep();
    }

    private static Rs2NpcModel nearestCreature()
    {
        // Demi-bosses first (Bear/Dragon/Dark Beast), then trash.
        for (int id : GauntletIds.DEMI_BOSSES)
        {
            Rs2NpcModel n = Rs2Npc.getNpc(id);
            if (n != null) return n;
        }
        for (int id : GauntletIds.CREATURES_ALL)
        {
            Rs2NpcModel n = Rs2Npc.getNpc(id);
            if (n != null) return n;
        }
        return null;
    }

    private static void waitSkill()
    {
        // Wait until we stop gaining (node depletes) or a short timeout.
        sleepUntil(() -> !Rs2Player.isMoving(), 3000);
        sleep(600, 1200);
    }

    // ------------------------------------------------------------------
    // Crafting / cooking / potions  (best-effort — confirm widgets in-game)
    // ------------------------------------------------------------------
    public static void craft(CorruptedGauntletConfig c)
    {
        // Cook raw fish first if any remain.
        if (Rs2Inventory.hasItem(GauntletIds.ITEM_RAW_FOOD)
            && Rs2Inventory.count(GauntletIds.ITEM_FOOD) < c.targetFood())
        {
            if (cook()) return;
        }

        // Make the ranged weapon (bow) — required minimum.
        if (!Rs2Inventory.hasItem(GauntletIds.ITEM_BOW))
        { makeAtBowl("bow"); return; }

        // Make the magic weapon (staff).
        if (!Rs2Inventory.hasItem(GauntletIds.ITEM_STAFF))
        { makeAtBowl("staff"); return; }

        // Armour (helm/body/legs) from ore/bark/linum.
        if (!Rs2Inventory.hasItem("Corrupted helm")) { makeAtBowl("helm"); return; }
        if (!Rs2Inventory.hasItem("Corrupted body")) { makeAtBowl("body"); return; }
        if (!Rs2Inventory.hasItem("Corrupted legs")) { makeAtBowl("legs"); return; }

        // Potions from grym leaves.
        if (Rs2Inventory.count(GauntletIds.ITEM_POTION) < c.targetPotions()
            && Rs2Inventory.hasItem(GauntletIds.ITEM_GRYM))
        { makePotion(); return; }
    }

    private static boolean cook()
    {
        if (!GauntletNav.interactObject(GauntletIds.OBJN_RANGE, "Cook")) return false;
        // Make-all interface: press the make-all / cook option.
        if (sleepUntil(() -> Rs2Widget.isWidgetVisible(GauntletIds.WIDGET_MAKE_GROUP, 14), 3000))
            Rs2Widget.clickWidget(GauntletIds.WIDGET_MAKE_GROUP, 14); // TODO confirm cook component
        sleepUntil(() -> !Rs2Inventory.hasItem(GauntletIds.ITEM_RAW_FOOD)
            || Rs2Player.isMoving(), 12000);
        return true;
    }

    /**
     * Open the Singing Bowl and select a recipe whose name contains {@code which}.
     * The Make interface (group 270) is sprite-based, so we click the option text
     * if present and otherwise fall through — the exact child id MUST be confirmed
     * in-game (run the tracer, read the click's wgroup.wchild for each recipe).
     */
    private static void makeAtBowl(String which)
    {
        Microbot.status = "[CG] crafting: " + which;
        if (!GauntletNav.interactObject(GauntletIds.OBJN_BOWL, "Make")
            && !GauntletNav.interactObject(GauntletIds.OBJN_BOWL, "Sing"))
            return;
        if (!sleepUntil(() -> Rs2Widget.isWidgetVisible(GauntletIds.WIDGET_MAKE_GROUP, 0), 3000))
            return;
        // TODO(in-game): map each recipe to its 270.<child>. For now try the option text.
        if (!Rs2Widget.clickWidget(which))
            Rs2Widget.clickWidget("Make");
        sleep(1200, 2000);
    }

    private static void makePotion()
    {
        // Egniol: crush grym leaf (pestle & mortar) -> grym potion (unf) with a
        // vial of water. Best-effort: use grym leaf, then the bowl/Make interface.
        Microbot.status = "[CG] brewing potion";
        if (Rs2Inventory.hasItem("Pestle and mortar") && Rs2Inventory.hasItem(GauntletIds.ITEM_GRYM))
        {
            Rs2Inventory.combine("Pestle and mortar", GauntletIds.ITEM_GRYM);
            sleep(600, 1000);
        }
        // TODO(in-game): finish unf -> egniol via vial of water; confirm interface.
        makeAtBowl("potion");
    }
}
