package net.runelite.client.plugins.microbot.corruptedgauntlet.enums;

/**
 * High-level states for a full Corrupted Gauntlet run. Derived each tick from the
 * world (region, NPCs, inventory) rather than hard step counters, so the loop
 * self-corrects if something happens out of order.
 */
public enum GauntletState
{
    /** In the lobby; start a Corrupted Gauntlet via the entrance. */
    LOBBY,

    /** In the dungeon prep maze: explore (light Nodes), gather, kill demi-bosses. */
    PREP_GATHER,

    /** Quotas met: craft weapons/armour/potions at the Singing Bowl and cook. */
    PREP_CRAFT,

    /** Ready: walk to the Barrier and enter the Hunllef arena (ranged prayer on). */
    ENTER_ARENA,

    /** Fighting the Corrupted Hunllef. */
    BOSS,

    /** Boss dead: loot the reward chest and leave to the lobby. */
    FINISH,

    /** Died (lost everything) or an unrecoverable state — stop, or restart from lobby. */
    DEAD,

    /** Nothing to do / waiting (login, loading). */
    IDLE
}
