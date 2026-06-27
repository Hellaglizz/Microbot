package net.runelite.client.plugins.microbot.doom.handlers;

import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/**
 * Weapon switching and special attacks, by item name. Names come from config and
 * default to the kit proven in the capture (Scorching bow / Crystal halberd /
 * Slayer's staff). Switches are idempotent — equip() no-ops if already wearing
 * the item, so it's safe to call every tick.
 */
public class GearHandler
{
    /** Equip an item by name if not already worn. Returns true if a swap was issued. */
    public boolean equip(String itemName)
    {
        if (itemName == null || itemName.isEmpty()) return false;
        if (Rs2Equipment.isWearing(itemName, false)) return false;
        return Rs2Inventory.interact(itemName, "Wield");
    }

    public boolean isWearing(String itemName)
    {
        return itemName != null && Rs2Equipment.isWearing(itemName, false);
    }

    /**
     * Equip {@code weapon} (if needed) and fire its special attack once enough
     * energy is available. setSpecState no-ops if energy is short, so this is
     * safe to gate loosely. Returns true if the spec was triggered.
     */
    public boolean spec(String weapon, int energyRequired)
    {
        if (weapon == null || weapon.isEmpty()) return false;
        if (equip(weapon)) return false;          // wait a tick for the swap to land
        if (!isWearing(weapon)) return false;
        return Rs2Combat.setSpecState(true, energyRequired * 10); // API uses 0-1000 scale
    }
}
