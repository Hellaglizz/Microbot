package net.runelite.client.plugins.microbot.corruptedgauntlet.logger;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(GauntletTraceLoggerConfig.GROUP)
public interface GauntletTraceLoggerConfig extends Config
{
    String GROUP = "cgauntlettracelogger";

    @ConfigItem(
        keyName = "writeText",
        name = "Write readable .txt",
        description = "Also write a human-readable per-tick narrative (trace-<ts>.txt) alongside the .jsonl.",
        position = 0
    )
    default boolean writeText() { return true; }

    @ConfigItem(
        keyName = "logPlayer",
        name = "Trace the player",
        description = "Per-tick: prev tile -> new tile -> delta/direction, walk/run, click destination, animation, equipped weapon, target, prayers, hp/pray/spec/run.",
        position = 1
    )
    default boolean logPlayer() { return true; }

    @ConfigItem(
        keyName = "logHunllef",
        name = "Trace the Hunllef",
        description = "Per-tick Hunllef movement, animation, derived attack style (from its NPC id), target and HP ratio — plus a hunllef_attack line each time it fires (with a running attack counter for the 6-attack weapon-swap rule).",
        position = 2
    )
    default boolean logHunllef() { return true; }

    @ConfigItem(
        keyName = "logTornadoes",
        name = "Trace tornadoes",
        description = "Per-tick position/trajectory of every Corrupted tornado so the dodge path can be reconstructed.",
        position = 3
    )
    default boolean logTornadoes() { return true; }

    @ConfigItem(
        keyName = "logCreatures",
        name = "Trace corrupted creatures",
        description = "Per-tick trace of corrupted creatures + demi-bosses (bear/dragon/dark beast) during the prep phase.",
        position = 4
    )
    default boolean logCreatures() { return true; }

    @ConfigItem(
        keyName = "logProjectiles",
        name = "Trace projectiles",
        description = "Per-tick projectile id -> style (magic/ranged/prayer-orb), source -> target and remaining cycles counting down to impact.",
        position = 5
    )
    default boolean logProjectiles() { return true; }

    @ConfigItem(
        keyName = "logPrayerSwitches",
        name = "Log prayer switches",
        description = "Emit a line on the exact tick your active prayer set changes (which prayer turned on/off).",
        position = 6
    )
    default boolean logPrayerSwitches() { return true; }

    @ConfigItem(
        keyName = "logResources",
        name = "Log resource objects",
        description = "Record spawns/despawns/deplete transitions of resource nodes (rock, tree, pond, herb, fibre), the singing bowl, range, sink, arena blockade and the danger floor tiles.",
        position = 7
    )
    default boolean logResources() { return true; }

    @ConfigItem(
        keyName = "logInventory",
        name = "Log inventory deltas",
        description = "Record EVERY inventory change in both directions — resources gathered, items crafted/cooked, supplies consumed — so the whole prep routine is captured tick-by-tick.",
        position = 8
    )
    default boolean logInventory() { return true; }

    @ConfigItem(
        keyName = "logEquipment",
        name = "Log gear swaps",
        description = "Record equipment changes per slot (the bow/staff/halberd weapon rotation) and the equipped weapon each tick.",
        position = 9
    )
    default boolean logEquipment() { return true; }

    @ConfigItem(
        keyName = "logMenuClicks",
        name = "Log your clicks",
        description = "Record every menu action you take (option/target/action/ids + clicked widget id/text/item), tick-stamped, so raw inputs and crafting-menu interactions are captured.",
        position = 10
    )
    default boolean logMenuClicks() { return true; }

    @ConfigItem(
        keyName = "logWidgets",
        name = "Log interface opens",
        description = "Record which interface group id opens (singing bowl / cooking / potion / dialogue UIs) so the prep crafting can be automated via Rs2Widget.",
        position = 11
    )
    default boolean logWidgets() { return true; }

    @ConfigItem(
        keyName = "logHitsplats",
        name = "Log hitsplats",
        description = "Record hitsplats on you and on the Hunllef / corrupted creatures (damage + timing).",
        position = 11
    )
    default boolean logHitsplats() { return true; }

    @ConfigItem(
        keyName = "logGraphics",
        name = "Log graphics objects",
        description = "Record graphics-object ids (tornado/crystal telegraphs, attack tile markers) with their tiles.",
        position = 12
    )
    default boolean logGraphics() { return true; }

    @ConfigItem(
        keyName = "logChat",
        name = "Log game messages",
        description = "Record game/chat messages (prayer drained, gauntlet start/finish, deaths, etc.).",
        position = 13
    )
    default boolean logChat() { return true; }
}
