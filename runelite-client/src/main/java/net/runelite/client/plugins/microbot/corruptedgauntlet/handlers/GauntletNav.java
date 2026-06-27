package net.runelite.client.plugins.microbot.corruptedgauntlet.handlers;

import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.corruptedgauntlet.data.GauntletIds;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Coordinate-agnostic maze navigation for the prep phase.
 *
 * Nothing here hard-codes tiles: every target is found by querying the live cache
 * for a named object, and movement is collision-BFS via Rs2Walker (which reads the
 * actual loaded scene). The maze is a 7x7 grid of 16x16 rooms whose connecting
 * walls are SOLID until you "Light" the Node at the centre of the shared edge (and
 * you must hold a Corrupted sceptre). So exploration = scan the loaded scene for
 * what we need; if it's not here, light a Node to open the next room and re-scan.
 */
public class GauntletNav
{
    /** ~12-tile room interior; "near enough to interact" reach. */
    private static final int REACH = 2;

    /** Walk to a named object and perform an action on it. */
    public static boolean interactObject(String name, String action)
    {
        GameObject go = Rs2GameObject.get(name, false);
        if (go == null) return false;
        WorldPoint wp = go.getWorldLocation();
        if (wp != null && Rs2Player.getWorldLocation().distanceTo(wp) > REACH)
        {
            Rs2Walker.walkTo(wp, REACH);
            sleepUntil(() -> !Rs2Player.isMoving(), 4000);
        }
        return Rs2GameObject.interact(name, action, false);
    }

    /** Is a needed resource object currently loaded in the scene? */
    public static boolean isPresent(String objectName)
    {
        return Rs2GameObject.get(objectName, false) != null;
    }

    /**
     * Open a new room by lighting an un-lit Node. The "Light" action only exists on
     * nodes that aren't open yet, so interacting "Node"/"Light" naturally targets a
     * fresh edge and skips already-opened passages. Returns true if a node was lit.
     */
    public static boolean exploreStep()
    {
        if (!hasSceptre())
        {
            Microbot.status = "[CG] missing Corrupted sceptre — cannot open rooms";
            return false;
        }
        GameObject node = Rs2GameObject.get(GauntletIds.OBJN_NODE, false);
        if (node == null) return false;
        WorldPoint wp = node.getWorldLocation();
        if (wp != null && Rs2Player.getWorldLocation().distanceTo(wp) > REACH)
        {
            Rs2Walker.walkTo(wp, REACH);
            sleepUntil(() -> !Rs2Player.isMoving(), 4000);
        }
        boolean lit = Rs2GameObject.interact(GauntletIds.OBJN_NODE, "Light", false);
        if (lit) sleepUntil(() -> !Rs2Player.isMoving(), 3000);
        return lit;
    }

    public static boolean hasSceptre()
    {
        return net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory
                   .hasItem(GauntletIds.ITEM_SCEPTRE)
            || net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment
                   .isWearing(GauntletIds.ITEM_SCEPTRE, false);
    }

    /** Walk to and Quick-pass the arena Barrier. */
    public static boolean enterArena()
    {
        return interactObject(GauntletIds.OBJN_BARRIER, "Quick-pass");
    }
}
