package net.runelite.client.plugins.microbot.doom.enums;

/**
 * High-level fight states. The fight is "rotation as data" — these states are
 * derived from boss form / animation / projectiles each tick, NOT hard-coded
 * step counters, so the loop self-corrects if the boss does something off-script.
 */
public enum DoomState
{
    /** Not in the arena, or waiting on the burrow/descend prompt before the boss spawns. */
    IDLE,

    /** Boss is up and attacking with its standard rotation (pray + DPS + dodge rocks). */
    STANDARD,

    /** Doom is preparing a melee charge alongside a rock throw — needs a melee punish. */
    MELEE_PUNISH,

    /** Rock throw in the air: dodge shadows, then re-pray after it lands. */
    ROCK_THROW,

    /** Volatile earth out: build and stand in an earthen shield before the shockwave. */
    SHOCKWAVE,

    /** Demonic shield up (delve 3+): demonbane the shield, kill grubs, manage charge. */
    SHIELD_PHASE,

    /** Burrowed "car" phase (delve 5+): keep attacking, walk the D1->D2->D3 transitions. */
    CAR_PHASE,

    /** Boss dead, descending to the next delve. */
    DESCEND,

    /** Emergency: out of supplies / unexpected state — stop and let the player take over. */
    PANIC
}
