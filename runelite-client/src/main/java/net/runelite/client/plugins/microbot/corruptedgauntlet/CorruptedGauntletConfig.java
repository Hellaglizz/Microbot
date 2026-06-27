package net.runelite.client.plugins.microbot.corruptedgauntlet;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(CorruptedGauntletConfig.GROUP)
public interface CorruptedGauntletConfig extends Config
{
    String GROUP = "corruptedgauntlet";

    @ConfigSection(name = "General", description = "Run behaviour", position = 0)
    String general = "general";

    @ConfigSection(name = "Prep", description = "Gathering / crafting targets", position = 1)
    String prep = "prep";

    @ConfigSection(name = "Boss", description = "Hunllef combat", position = 2)
    String boss = "boss";

    // ---- General ----
    @ConfigItem(keyName = "autoRestart", name = "Auto-restart", description =
        "After a kill (or death), re-enter the Gauntlet and keep farming.",
        section = general, position = 0)
    default boolean autoRestart() { return true; }

    @ConfigItem(keyName = "stopOnDeath", name = "Stop on death", description =
        "If you die (lose supplies), halt instead of restarting.",
        section = general, position = 1)
    default boolean stopOnDeath() { return true; }

    // ---- Prep targets ----
    @ConfigItem(keyName = "targetShards", name = "Target shards", description =
        "Crystal shards to gather before crafting (≈500-560 for 2 potions).",
        section = prep, position = 0)
    default int targetShards() { return 560; }

    @ConfigItem(keyName = "targetOre", name = "Ore / bark / linum", description =
        "How many of corrupted ore, phren bark and linum tirinum to gather (7 = attuned armour).",
        section = prep, position = 1)
    default int targetOre() { return 7; }

    @ConfigItem(keyName = "targetGrym", name = "Grym leaves", description =
        "Grym leaves to gather for potions.",
        section = prep, position = 2)
    default int targetGrym() { return 3; }

    @ConfigItem(keyName = "targetFood", name = "Cooked food", description =
        "Paddlefish to cook before the boss.",
        section = prep, position = 3)
    default int targetFood() { return 24; }

    @ConfigItem(keyName = "targetPotions", name = "Potions", description =
        "Egniol potions to brew.",
        section = prep, position = 4)
    default int targetPotions() { return 2; }

    // ---- Boss ----
    @ConfigItem(keyName = "eatAtPercent", name = "Eat at HP %", description =
        "Eat a paddlefish when HP drops below this percent.",
        section = boss, position = 0)
    default int eatAtPercent() { return 50; }

    @ConfigItem(keyName = "useOffensivePrayers", name = "Offensive prayers", description =
        "Flick Eagle Eye (bow) / Mystic Might (staff) while attacking.",
        section = boss, position = 1)
    default boolean useOffensivePrayers() { return true; }
}
