package net.runelite.client.plugins.microbot.doom.data;

/**
 * ============================================================================
 *  RUNTIME IDs — filled from a real delve-1..8 capture (doom-20260618-025424).
 * ============================================================================
 *
 * Confidence is high for everything tagged "verified". The only gap is the
 * giant *ranged* larva, which didn't spawn in the capture run.
 */
public final class DoomIds
{
    private DoomIds() {}

    // ---- Boss NPC ids (verified) -------------------------------------
    public static final int DOOM_NORMAL    = 14707; // "Doom of Mokhaiotl"
    public static final int DOOM_SHIELDED  = 14708; // "Doom of Mokhaiotl (Shielded)"
    public static final int DOOM_BURROWED  = 14709; // "Doom of Mokhaiotl (Burrowed)"
    public static final int[] DOOM_ALL     = { DOOM_NORMAL, DOOM_SHIELDED, DOOM_BURROWED };

    // ---- Demonic larva NPC ids (verified; colour == style at delve 4+) -
    public static final int LARVA_NEUTRAL  = 14710; // "Demonic larva" (delve 1-3, uncoloured)
    public static final int LARVA_RANGED   = 14711; // "Demonic range larva"
    public static final int LARVA_MAGIC    = 14712; // "Demonic magic larva"
    public static final int LARVA_MELEE    = 14713; // "Demonic melee larva" (shield phase only)
    public static final int LARVA_GIANT_MAGIC  = 14789; // "Giant demonic magic larva" (delve 8+)
    public static final int LARVA_GIANT_RANGED = 14788; // "Giant demonic range larva" (delve 8+)
    public static final int[] LARVA_ALL    = {
        LARVA_NEUTRAL, LARVA_RANGED, LARVA_MAGIC, LARVA_MELEE,
        LARVA_GIANT_MAGIC, LARVA_GIANT_RANGED
    };

    // ---- Mechanic NPCs (verified — these are NPCs, not game objects) --
    public static final int NPC_VOLATILE_EARTH = 14714; // attack two to spawn the earthen shield
    public static final int NPC_EARTHEN_SHIELD = 14715; // stand in it to survive the shockwave

    // ---- Projectile ids (verified via prayer-correlation) ------------
    // Standard attack telegraph. Damage computes on impact; flick to match.
    // The rock-throw follow-up REUSES the magic/ranged ids below, so this
    // mapping already covers rock throws — no separate rock projectile needed.
    public static final int PROJ_MELEE     = 3378; // player held Protect Melee at impact
    public static final int PROJ_MAGIC     = 3379; // player held Protect Magic
    public static final int PROJ_RANGED    = 3380; // player held Protect Missiles
    public static final int PROJ_ROCK_MAGIC  = 3379; // same id, kept for readability
    public static final int PROJ_ROCK_RANGED = 3380;
    /** Player's own weapon projectiles (reference only; ignore for prayer). */
    public static final int PROJ_PLAYER_BOW   = 1384;
    public static final int PROJ_PLAYER_BURROW= 1936;

    // ---- Graphics objects (verified) ---------------------------------
    public static final int GFX_ROCK_SHADOW   = 2380; // looming shadow / AoE danger tile (also shockwave)
    public static final int GFX_ROCK_SHADOW_2 = 3404; // paired second layer (same tiles, +1 tick)
    public static final int GFX_BURROW_EYE    = 3415; // yellow eye marking the car destination
    public static final int GFX_BURROW_EYE_2  = 3416; // paired

    // ---- Game objects (verified) -------------------------------------
    public static final int OBJ_LANDED_ROCK   = 57286; // rock that stays after a throw (cover / rockblock)
    public static final int OBJ_CAR_ROCK      = 29733; // falling rocks during the car phase
    public static final int OBJ_ACID_BLOOD    = 57283; // venom splat (delve 3+); avoid standing on it

    // ---- Doom animation ids (verified) -------------------------------
    public static final int ANIM_SHIELD_UP    = 12408; // precedes the shield phase
    public static final int ANIM_SHIELD_UP_2  = 12409; // paired
    /** Melee-charge wind-up: occurs ONLY during a melee charge (14/14 in capture). */
    public static final int ANIM_MELEE_CHARGE = 12409;
    // Burrow has no wind-up anim — the normal NPC despawns and the burrowed NPC
    // spawns, so detect the car phase by DOOM_BURROWED presence, not animation.
    // Shockwave is detected by NPC_VOLATILE_EARTH presence, not animation.

    // ---- Descend / claim (verified) ---------------------------------
    /** "Burrow hole" object spawned after each delve; Investigate -> Descend/Claim. */
    public static final int OBJ_BURROW_HOLE   = 57285;
    // Dialog options are clicked by visible text (stable), child ids for reference:
    //   "Descend"          919.23
    //   "Claim and leave"  919.14
    //   "Confirm"          289.8
    //   "Bank-all"         919.26
    //   "Leave"            919.16

    // ---- Lobby / re-entry (verified) --------------------------------
    public static final int OBJ_GAP_ENTER     = 57289; // "Jump-over" — drop into delve 1
    public static final int OBJ_GAP_EXIT      = 57290; // "Exit / Quick-exit"
    public static final int OBJ_DOOM_ENTRANCE = 51375; // "Pass-through" entrance to the lobby area
    public static final int LOBBY_REGION      = 5269;

    // ---- Resupply travel (verified from one capture — EXPERIMENTAL) -
    public static final int OBJ_ORNATE_POOL   = 29241; // POH "Ornate pool of Rejuvenation" (Drink)
    public static final int OBJ_CW_BANK_CHEST = 4483;  // Castle Wars "Bank chest" (Use)
    public static final int NPC_TONALI_TELE   = 14419; // "Tonali Teleporter" (Varlamore network -> near Doom)

    // ---- Hitsplat type for the melee-punish bonus hit (verified) -----
    public static final int HITSPLAT_MELEE_PUNISH = 43;

    // ---- Misc --------------------------------------------------------
    public static final int REGION_ID         = 14180;
}
