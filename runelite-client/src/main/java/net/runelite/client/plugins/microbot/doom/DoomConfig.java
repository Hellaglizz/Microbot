package net.runelite.client.plugins.microbot.doom;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(DoomConfig.GROUP)
public interface DoomConfig extends Config
{
    String GROUP = "doom";

    @ConfigItem(
        keyName = "maxDelve",
        name = "Max delve (0 = unlimited)",
        description = "Hard cap on delve level. 0 means keep pushing into deep delves, gated only by supplies / uniques.",
        position = 0
    )
    @Range(min = 0, max = 30)
    default int maxDelve() { return 0; }

    @ConfigItem(
        keyName = "brewAt",
        name = "Brew at HP",
        description = "Saradomin brew when HP drops below this. Brew is an emergency only; the ancient gods sword spec is the main heal.",
        position = 1
    )
    @Range(min = 1, max = 99)
    default int brewAt() { return 35; }

    @ConfigItem(
        keyName = "restoreAt",
        name = "Restore at prayer",
        description = "Super restore / brew-down recovery when prayer points fall below this.",
        position = 2
    )
    @Range(min = 1, max = 99)
    default int restoreAt() { return 15; }

    @ConfigItem(
        keyName = "useScytheForPunish",
        name = "Scythe punish on 1-5",
        description = "Use the scythe for melee punishes up to delve 5, then swap to halberd on 6+ to reduce acid splatter.",
        position = 3
    )
    default boolean useScytheForPunish() { return true; }

    @ConfigItem(
        keyName = "rockblock",
        name = "Rockblock (delve 6+)",
        description = "Attempt the rockblock setup on delve 6+ car phases instead of plain transitions.",
        position = 4
    )
    default boolean rockblock() { return true; }

    @ConfigItem(
        keyName = "blowpipeInCarPhase",
        name = "Blowpipe in car phase",
        description = "Use the blowpipe during the car phase when close enough; fall back to the main bow at range.",
        position = 5
    )
    default boolean blowpipeInCarPhase() { return true; }

    @ConfigItem(
        keyName = "healSpecSword",
        name = "Ancient gods sword heal",
        description = "Use the ancient gods sword spec during shield phase as free healing. Off unless you actually carry it.",
        position = 6
    )
    default boolean healSpecSword() { return false; }

    @ConfigItem(
        keyName = "rangeInvSetup",
        name = "Range setup name",
        description = "InventorySetups name for the main ranged gear (twisted/scorching bow).",
        position = 7
    )
    default String rangeInvSetup() { return "Doom Range"; }

    @ConfigItem(
        keyName = "demonbaneInvSetup",
        name = "Demonbane setup name",
        description = "InventorySetups name for the demonbane / shield-phase gear.",
        position = 8
    )
    default String demonbaneInvSetup() { return "Doom Demonbane"; }

    // ------------------------------------------------------------------
    // Humanization. Idle behaviour only ever fires in safe windows
    // (STANDARD / SHIELD at healthy HP) — never during a lethal mechanic.
    // ------------------------------------------------------------------

    @ConfigItem(
        keyName = "idleChance",
        name = "Idle filler chance %",
        description = "Per-safe-tick chance of a cosmetic idle action (camera nudge / mouse drift / pause). 0 disables.",
        position = 9
    )
    @Range(min = 0, max = 30)
    default int idleChance() { return 6; }

    @ConfigItem(
        keyName = "cameraChance",
        name = "Camera-nudge share %",
        description = "Of idle actions, the share that is a small camera rotation / re-centre on the boss.",
        position = 10
    )
    @Range(min = 0, max = 100)
    default int cameraChance() { return 50; }

    @ConfigItem(
        keyName = "microBreaks",
        name = "Micro-breaks between waves",
        description = "Allow the antiban stack to take a short break BETWEEN delves only (never mid-fight).",
        position = 11
    )
    default boolean microBreaks() { return true; }

    @ConfigItem(
        keyName = "wrongTileClicks",
        name = "Wrong-tile clicks (risky)",
        description = "Occasionally click a verified-safe neighbour of the target tile, then correct. Safe-window only, but it can still nudge your position — leave off unless you want it.",
        position = 12
    )
    default boolean wrongTileClicks() { return false; }

    @ConfigItem(
        keyName = "wrongClickChance",
        name = "Wrong-click chance %",
        description = "Per safe-window walk, chance of the wrong-then-correct pattern.",
        position = 13
    )
    @Range(min = 0, max = 25)
    default int wrongClickChance() { return 4; }

    // ------------------------------------------------------------------
    // Weapon names (exact in-game item names). Defaults match the capture kit.
    // ------------------------------------------------------------------

    @ConfigItem(keyName = "weaponMain", name = "Main bow", description = "Primary ranged weapon.", position = 14)
    default String weaponMain() { return "Scorching bow"; }

    @ConfigItem(keyName = "weaponPunish", name = "Melee-punish weapon", description = "Halberd-type weapon for melee punishes (1 tile reach).", position = 15)
    default String weaponPunish() { return "Crystal halberd"; }

    @ConfigItem(keyName = "weaponMagicGrub", name = "Magic-grub weapon", description = "Demonbane autocast for magic larvae / shield (e.g. Slayer's staff).", position = 16)
    default String weaponMagicGrub() { return "Slayer's staff"; }

    @ConfigItem(keyName = "weaponMeleeDemonbane", name = "Melee demonbane", description = "Demonbane melee for melee larvae / shield.", position = 17)
    default String weaponMeleeDemonbane() { return "Darklight"; }

    @ConfigItem(keyName = "weaponBlowpipe", name = "Blowpipe", description = "Short-range weapon for the car phase when in range (e.g. Toxic blowpipe).", position = 18)
    default String weaponBlowpipe() { return "Toxic blowpipe"; }

    @ConfigItem(keyName = "weaponHealSpec", name = "Heal-spec weapon", description = "Weapon whose spec heals during shield phase (ancient gods sword).", position = 19)
    default String weaponHealSpec() { return "Ancient godsword"; }

    @ConfigItem(keyName = "blowpipeRange", name = "Blowpipe range", description = "Use the blowpipe in car phase only within this many tiles of the boss (approximate — tune to taste).", position = 20)
    @Range(min = 1, max = 10)
    default int blowpipeRange() { return 5; }

    // ------------------------------------------------------------------
    // Supplies (exact item names + thresholds). Defaults match the capture kit.
    // ------------------------------------------------------------------

    @ConfigItem(keyName = "foodName", name = "Food", description = "Eaten when HP% drops below 'Food at HP%'.", position = 21)
    default String foodName() { return "Anglerfish"; }

    @ConfigItem(keyName = "foodAt", name = "Food at HP%", description = "Eat food below this HP percentage.", position = 22)
    @Range(min = 1, max = 99)
    default int foodAt() { return 50; }

    @ConfigItem(keyName = "prayerPotionName", name = "Prayer potion", description = "Drunk when prayer% drops below 'Prayer at %'.", position = 23)
    default String prayerPotionName() { return "Prayer potion"; }

    @ConfigItem(keyName = "prayerAt", name = "Prayer at %", description = "Drink a prayer potion below this prayer percentage.", position = 24)
    @Range(min = 1, max = 99)
    default int prayerAt() { return 45; }

    @ConfigItem(keyName = "antiVenomName", name = "Anti-venom", description = "Drunk when venomed and no anti-venom effect is active (delve 3+).", position = 25)
    default String antiVenomName() { return "Anti-venom"; }

    @ConfigItem(keyName = "emergencyBrewName", name = "Emergency food/brew", description = "Used when HP% drops below 'Brew at HP' (the emergency from the main settings).", position = 26)
    default String emergencyBrewName() { return "Saradomin brew"; }

    // ------------------------------------------------------------------
    // Spec economy
    // ------------------------------------------------------------------

    @ConfigItem(keyName = "deathCharge", name = "Cast Death Charge", description = "Cast Death Charge on cooldown to refund spec on the next delve-boss kill.", position = 27)
    default boolean deathCharge() { return true; }

    // ------------------------------------------------------------------
    // Session loop: continue vs bank
    // ------------------------------------------------------------------

    @ConfigItem(keyName = "bankOnLowSupplies", name = "Bank on low supplies", description = "Stop pushing deeper and bank when a required consumable runs out.", position = 28)
    default boolean bankOnLowSupplies() { return true; }

    @ConfigItem(keyName = "requireFood", name = "Require food to continue", description = "", position = 29)
    default boolean requireFood() { return true; }

    @ConfigItem(keyName = "requirePrayer", name = "Require prayer pots to continue", description = "", position = 30)
    default boolean requirePrayer() { return true; }

    @ConfigItem(keyName = "requireBrew", name = "Require brew to continue", description = "", position = 31)
    default boolean requireBrew() { return true; }

    @ConfigItem(keyName = "requireAntiVenom", name = "Require anti-venom to continue", description = "", position = 32)
    default boolean requireAntiVenom() { return false; }

    // ------------------------------------------------------------------
    // Banking / resupply (UNVERIFIED — no bank-trip captured yet).
    // While disabled, a needed bank trip stops the script safely instead of
    // guessing travel at the boss.
    // ------------------------------------------------------------------

    @ConfigItem(keyName = "bankingEnabled", name = "Auto-bank + resupply", description = "EXPERIMENTAL: leave, bank, reload setup and re-enter. Off until the bank trip is captured and wired.", position = 33)
    default boolean bankingEnabled() { return false; }

    @ConfigItem(keyName = "inventorySetup", name = "Inventory setup name", description = "InventorySetups loadout to restock to when banking.", position = 34)
    default String inventorySetup() { return "Doom"; }

    @ConfigItem(keyName = "uniqueChatPattern", name = "Unique drop pattern", description = "Regex matched against game messages to detect a unique drop (triggers a bank trip). Confirm against a real drop.", position = 35)
    default String uniqueChatPattern() { return "(?i)Eye of Ayak|Mokhaiotl cloth|Avernic|funny feeling|new item|untradeable drop|added to your collection log"; }
}
