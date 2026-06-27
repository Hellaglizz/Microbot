package net.runelite.client.plugins.microbot.corruptedgauntlet.data;

import java.util.HashSet;
import java.util.Set;

/**
 * ============================================================================
 *  Corrupted Gauntlet IDs (seed table for the tracer).
 * ============================================================================
 *
 * These are pulled from RuneLite's gameval tables (NpcID / ObjectID1 /
 * SpotanimID). The "_HM" / corrupted variants are what the Corrupted Gauntlet
 * uses; the normal-gauntlet ids are kept alongside for reference/robustness.
 *
 * The tracer does NOT rely on this list being complete — it captures every
 * nearby NPC / object / projectile / graphic by id+name so anything unknown is
 * still recorded and can be promoted into here after a capture. This is just
 * the high-confidence seed so the readable trace can label things.
 */
public final class GauntletIds
{
    private GauntletIds() {}

    // ====================================================================
    //  BOSS — Corrupted Hunllef
    // ====================================================================
    // IMPORTANT (verified from traces): the Hunllef's NPC id is the style it is
    // currently PROTECTING (its overhead prayer), NOT the style it is attacking
    // with. The two are independent cycles:
    //   - PROTECTION (this id / overhead): flips ~every 24-36 ticks ("every 6
    //     off-prayer hits you land"). You ATTACK with a style it is NOT protecting,
    //     so this id drives YOUR WEAPON swap.
    //   - ATTACK STYLE: read from its projectile (PROJ_MAGIC / PROJ_RANGED below),
    //     flips every 4 attacks (~20t), starts ranged. This drives YOUR PROTECTION
    //     PRAYER. See projectileStyle().
    // The id values match the gameval names: 9035 protect-melee, 9036 protect-
    // ranged, 9037 protect-magic. (Run2 = halberd+bow only ever saw 9035/9036.)
    public static final int HUNLLEF_PROT_MELEE  = 9035; // overhead: Protect from Melee
    public static final int HUNLLEF_PROT_RANGED = 9036; // overhead: Protect from Missiles
    public static final int HUNLLEF_PROT_MAGIC  = 9037; // overhead: Protect from Magic
    public static final int HUNLLEF_DEATH   = 9038; // "Corrupted Hunllef" (death anim)
    public static final int HUNLLEF_TORNADO = 9039; // "Corrupted ... crystals" — the chasing tornado NPC
    public static final int[] HUNLLEF_ALL = {
        HUNLLEF_PROT_MELEE, HUNLLEF_PROT_RANGED, HUNLLEF_PROT_MAGIC, HUNLLEF_DEATH
    };

    // Normal-gauntlet Hunllef (reference; in case the logger is run there too).
    public static final int HUNLLEF_MELEE_NORMAL   = 9021;
    public static final int HUNLLEF_RANGED_NORMAL  = 9022;
    public static final int HUNLLEF_MAGIC_NORMAL   = 9023;
    public static final int HUNLLEF_DEATH_NORMAL   = 9024;
    public static final int HUNLLEF_TORNADO_NORMAL = 9025;

    /**
     * Map a Hunllef NPC id to the style it is PROTECTING against (its overhead).
     * You attack with a style this is NOT, i.e. it drives your weapon choice —
     * it is NOT the boss's attack style (that comes from its projectile).
     */
    public static String hunllefProtection(int npcId)
    {
        if (npcId == HUNLLEF_PROT_MELEE  || npcId == HUNLLEF_MELEE_NORMAL)  return "melee";
        if (npcId == HUNLLEF_PROT_RANGED || npcId == HUNLLEF_RANGED_NORMAL) return "ranged";
        if (npcId == HUNLLEF_PROT_MAGIC  || npcId == HUNLLEF_MAGIC_NORMAL)  return "magic";
        if (npcId == HUNLLEF_DEATH  || npcId == HUNLLEF_DEATH_NORMAL)  return "dead";
        if (npcId == HUNLLEF_TORNADO || npcId == HUNLLEF_TORNADO_NORMAL) return "tornado";
        return null;
    }

    // ====================================================================
    //  CORRUPTED CREATURES (prep phase — demi-bosses + small spawns)
    // ====================================================================
    // Small creatures.
    public static final int RAT      = 9040; // "Corrupted Rat"
    public static final int SPIDER   = 9041; // "Corrupted Spider"
    public static final int BAT      = 9042; // "Corrupted Bat"
    public static final int UNICORN  = 9043; // "Corrupted Unicorn"
    public static final int SCORPION = 9044; // "Corrupted Scorpion"
    public static final int WOLF     = 9045; // "Corrupted Wolf"
    // Demi-bosses (drop weapon/armour seeds + crystal shards).
    public static final int BEAR       = 9046; // "Corrupted Bear"
    public static final int DRAGON     = 9047; // "Corrupted Dragon"
    public static final int DARK_BEAST = 9048; // "Corrupted Dark Beast"
    public static final int[] CREATURES_ALL = {
        RAT, SPIDER, BAT, UNICORN, SCORPION, WOLF, BEAR, DRAGON, DARK_BEAST
    };
    public static final int[] DEMI_BOSSES = { BEAR, DRAGON, DARK_BEAST };

    /** Every Corrupted-Gauntlet-relevant NPC id (boss + tornado + creatures). */
    public static final Set<Integer> ALL_NPCS = new HashSet<>();
    static
    {
        for (int id : HUNLLEF_ALL) ALL_NPCS.add(id);
        ALL_NPCS.add(HUNLLEF_TORNADO);
        for (int id : CREATURES_ALL) ALL_NPCS.add(id);
        // Normal gauntlet (reference).
        ALL_NPCS.add(HUNLLEF_MELEE_NORMAL);
        ALL_NPCS.add(HUNLLEF_RANGED_NORMAL);
        ALL_NPCS.add(HUNLLEF_MAGIC_NORMAL);
        ALL_NPCS.add(HUNLLEF_DEATH_NORMAL);
        ALL_NPCS.add(HUNLLEF_TORNADO_NORMAL);
    }

    // ====================================================================
    //  RESOURCE / SCENERY OBJECTS (corrupted = "_HM")
    // ====================================================================
    public static final int OBJ_SINGING_BOWL   = 35966; // craft weapons/armour
    public static final int OBJ_ROCK            = 35967; // mine -> corrupted ore + shards
    public static final int OBJ_ROCK_DEPLETED   = 35968;
    public static final int OBJ_TREE            = 35969; // chop -> phren bark
    public static final int OBJ_TREE_DEPLETED   = 35970;
    public static final int OBJ_POND            = 35971; // fish -> raw fish
    public static final int OBJ_POND_DEPLETED   = 35972;
    public static final int OBJ_HERB            = 35973; // pick -> grym leaf
    public static final int OBJ_HERB_DEPLETED   = 35974;
    public static final int OBJ_FIBRE           = 35975; // harvest -> linum tirinum
    public static final int OBJ_FIBRE_DEPLETED  = 35976;
    public static final int OBJ_TOOLS           = 35977; // teleport/grand exchange-ish utility
    public static final int OBJ_RANGE           = 35980; // cook raw fish
    public static final int OBJ_SINK            = 35981; // fill vials with water
    public static final int OBJ_BLOCKADE_ENTER  = 35982; // enter the Hunllef arena
    public static final int OBJ_BLOCKADE_ESCAPE = 35983; // leave the arena
    public static final int OBJ_LOBBY_ENTRANCE  = 35984;
    public static final int OBJ_LOBBY_EXIT      = 35985;
    public static final int OBJ_ENTRANCE        = 35986; // start a Corrupted Gauntlet
    public static final int OBJ_CHEST_CLOSED    = 35988; // reward chest (closed)
    public static final int OBJ_CHEST_OPEN      = 35989; // reward chest (open)

    // Room-unlock "Node" at the centre of each room edge (verified from trace).
    // Action "Light" (requires a Corrupted sceptre in inv/worn) opens the passage
    // to the adjacent room. Two ids alternate (lit/orientation variants). The walls
    // between rooms are SOLID collision until the connecting node is lit, so cross-
    // room pathing must Light the edge node first. ~30 lit per run.
    public static final int OBJ_NODE_A = 35998; // "Node" (Light)
    public static final int OBJ_NODE_B = 35999; // "Node" (Light)

    // Hunllef-arena floor tiles — the stomp/crystal danger telegraph.
    public static final int TILE_WARNING = 36047; // PRIF_GAUNTLET_FLOOR_TILE_01_WARNING_HM (avoid)
    public static final int TILE_HIT     = 36048; // PRIF_GAUNTLET_FLOOR_TILE_01_HIT_HM (active damage)

    public static final Set<Integer> RESOURCE_OBJS = new HashSet<>();
    static
    {
        for (int id : new int[]{
            OBJ_SINGING_BOWL, OBJ_ROCK, OBJ_ROCK_DEPLETED, OBJ_TREE, OBJ_TREE_DEPLETED,
            OBJ_POND, OBJ_POND_DEPLETED, OBJ_HERB, OBJ_HERB_DEPLETED, OBJ_FIBRE,
            OBJ_FIBRE_DEPLETED, OBJ_TOOLS, OBJ_RANGE, OBJ_SINK, OBJ_BLOCKADE_ENTER,
            OBJ_BLOCKADE_ESCAPE, OBJ_LOBBY_ENTRANCE, OBJ_LOBBY_EXIT, OBJ_ENTRANCE,
            OBJ_CHEST_CLOSED, OBJ_CHEST_OPEN, TILE_WARNING, TILE_HIT
        }) RESOURCE_OBJS.add(id);
    }

    // ====================================================================
    //  PROJECTILES / SPOTANIMS (corrupted = "_HM")
    // ====================================================================
    // Projectile.getId() matches these spotanim ids.
    public static final int PROJ_MAGIC   = 1708; // CRYSTAL_HUNLLEF_MAGIC_TRAVEL_HM  (pray Magic)
    public static final int PROJ_RANGED  = 1712; // CRYSTAL_HUNLLEF_RANGE_TRAVEL_HM  (pray Missiles)
    public static final int PROJ_PRAYER  = 1714; // CRYSTAL_HUNLLEF_PRAYER_TRAVEL_HM (the prayer-disable orb)
    public static final int PROJ_CRYSTALS = 1718; // CRYSTAL_HUNLLEF_CRYSTALS_HIT_HM (tornado/crystal damage)
    // Normal-gauntlet variants (reference).
    public static final int PROJ_MAGIC_NORMAL    = 1707;
    public static final int PROJ_RANGED_NORMAL   = 1711;
    public static final int PROJ_PRAYER_NORMAL   = 1713;
    public static final int PROJ_CRYSTALS_NORMAL = 1717;

    /**
     * Map a projectile id to the boss's ATTACK style (== the protection prayer you
     * must hold). This is the boss's real attack-style cycle (flips every 4
     * attacks, starts ranged), independent of what it is protecting (its id).
     * null = unknown/your own projectile.
     */
    public static String projectileStyle(int id)
    {
        if (id == PROJ_MAGIC  || id == PROJ_MAGIC_NORMAL)  return "magic";
        if (id == PROJ_RANGED || id == PROJ_RANGED_NORMAL) return "ranged";
        if (id == PROJ_PRAYER || id == PROJ_PRAYER_NORMAL) return "prayer-orb";
        if (id == PROJ_CRYSTALS || id == PROJ_CRYSTALS_NORMAL) return "crystals";
        return null;
    }

    // ====================================================================
    //  ITEM NAMES (magic+ranged build) + interfaces
    // ====================================================================
    public static final String ITEM_SCEPTRE   = "Corrupted sceptre";     // light Nodes
    public static final String ITEM_BOW       = "Corrupted bow";         // ranged weapon (any tier)
    public static final String ITEM_STAFF     = "Corrupted staff";       // magic weapon (any tier)
    public static final String ITEM_FOOD      = "Paddlefish";            // cooked food
    public static final String ITEM_RAW_FOOD  = "Raw paddlefish";
    public static final String ITEM_POTION    = "Egniol potion";
    public static final String ITEM_SHARDS    = "Corrupted shards";
    public static final String ITEM_ORE       = "Corrupted ore";
    public static final String ITEM_BARK      = "Phren bark";
    public static final String ITEM_LINUM     = "Linum tirinum";
    public static final String ITEM_GRYM      = "Grym leaf";

    // Resource node object names (interact by name; they drop the ground item).
    public static final String OBJN_DEPOSIT   = "Corrupt Deposit";       // Mine -> ore
    public static final String OBJN_PHREN     = "Corrupt Phren Roots";   // Chop -> bark
    public static final String OBJN_FISHING   = "Corrupt Fishing Spot";  // Fish -> raw paddlefish (+ water)
    public static final String OBJN_GRYM      = "Corrupt Grym Root";     // Pick -> grym leaf
    public static final String OBJN_LINUM     = "Corrupt Linum Tirinum"; // Pick -> linum
    public static final String OBJN_NODE      = "Node";                  // Light -> open room (needs sceptre)
    public static final String OBJN_BOWL      = "Singing Bowl";          // Make interface (group 270)
    public static final String OBJN_RANGE     = "Range";                 // Cook
    public static final String OBJN_BARRIER   = "Barrier";               // Quick-pass -> arena

    /** Singing Bowl crafting uses the standard "Make-X" interface, group 270. */
    public static final int WIDGET_MAKE_GROUP = 270;

    // ====================================================================
    //  REGION
    // ====================================================================
    // Corrupted Gauntlet lobby + instance regions (the instanced dungeon reuses
    // a region id; confirmed from the capture, kept loose so the tracer never
    // gates incorrectly).
    public static final int REGION_LOBBY = 12127;
}
