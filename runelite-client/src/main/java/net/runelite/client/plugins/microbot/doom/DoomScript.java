package net.runelite.client.plugins.microbot.doom;

import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.doom.data.DoomIds;
import net.runelite.client.plugins.microbot.doom.data.DoomTiles;
import net.runelite.client.plugins.microbot.doom.enums.DoomState;
import net.runelite.client.plugins.microbot.doom.handlers.DoomQuery;
import net.runelite.client.plugins.microbot.doom.handlers.BankRoutine;
import net.runelite.client.plugins.microbot.doom.handlers.GearHandler;
import net.runelite.client.plugins.microbot.doom.handlers.Humanizer;
import net.runelite.client.plugins.microbot.doom.handlers.LarvaHandler;
import net.runelite.client.plugins.microbot.doom.handlers.PhaseTracker;
import net.runelite.client.plugins.microbot.doom.handlers.PrayerHandler;
import net.runelite.client.plugins.microbot.doom.handlers.RockThrowHandler;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Core loop. Each tick: survival -> read state from the world -> dispatch.
 * Rotation is data (PhaseTracker on the queryable cache), not a step counter,
 * so the loop self-heals. Humanizer drives Microbot's antiban in safe windows.
 */
public class DoomScript extends Script
{
    public static double version = 1.0;

    private final PhaseTracker phase = new PhaseTracker();
    private final PrayerHandler prayer = new PrayerHandler();
    private final RockThrowHandler rocks = new RockThrowHandler();
    private final LarvaHandler larvae = new LarvaHandler();
    private final GearHandler gear = new GearHandler();
    private final SpellHandler spells = new SpellHandler();
    private final SessionManager session = new SessionManager();
    private final BankRoutine bankRoutine = new BankRoutine();
    private final Humanizer human = new Humanizer();

    public SessionManager session() { return session; }
    public void setDelveLevel(int d) { phase.setDelveLevel(d); }

    private DoomConfig cfg;
    private DoomState lastState = DoomState.IDLE;

    public DoomState currentState() { return lastState; }
    public int delveLevel() { return phase.getDelveLevel(); }
    public Humanizer humanizer() { return human; }

    public boolean run(DoomConfig config)
    {
        this.cfg = config;
        Microbot.enableAutoRunOn = false;

        human.setIdleChance(cfg.idleChance() / 100.0);
        human.setCameraChance(cfg.cameraChance() / 100.0);
        human.setMicroBreaks(cfg.microBreaks());
        human.setWrongClicks(cfg.wrongTileClicks(), cfg.wrongClickChance() / 100.0);
        human.init();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!Microbot.isLoggedIn() || !super.run()) return;
                if (!survival()) return;

                DoomState state = phase.resolve();
                lastState = state;
                Rs2NpcModel doom = phase.findDoom();

                switch (state)
                {
                    case IDLE:          handleIdle();              break;
                    case STANDARD:      handleStandard(doom);      break;
                    case MELEE_PUNISH:  handleMeleePunish(doom);   break;
                    case ROCK_THROW:    handleRockThrow(doom);     break;
                    case SHOCKWAVE:     handleShockwave(doom);     break;
                    case SHIELD_PHASE:  handleShieldPhase(doom);   break;
                    case CAR_PHASE:     handleCarPhase(doom);      break;
                    case DESCEND:       handleDescend();           break;
                    case PANIC:         break;
                }

                human.maybeIdle(state, hpPercent(), doom != null ? doom.getNpc() : null);
            }
            catch (Exception ex)
            {
                Microbot.log("Doom loop error: " + ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    // ---- survival ----------------------------------------------------
    // Returns false if a consumable was used this tick (skip combat for a tick
    // to avoid stacking two menu actions). Order: emergency brew > food > prayer
    // > anti-venom.
    private boolean survival()
    {
        int hp = hpPercent();
        int pray = prayerPercent();

        if (hp <= cfg.brewAt())
        {
            eat(cfg.emergencyBrewName());   // brews use "Drink"; handled in consume()
            return false;
        }
        if (hp <= cfg.foodAt())
        {
            eat(cfg.foodName());
            return false;
        }
        if (pray <= cfg.prayerAt())
        {
            drink(cfg.prayerPotionName());
            return false;
        }
        if (phase.getDelveLevel() >= 3
            && !Rs2Player.hasAntiVenomActive()
            && Microbot.getClient().getVarpValue(VarPlayer.POISON) > 0)
        {
            drink(cfg.antiVenomName());
            return false;
        }
        return true;
    }

    private void drink(String name)
    {
        if (name == null || name.isEmpty()) return;
        human.react();
        Rs2Inventory.interact(name, "Drink");
    }

    /** Food uses "Eat"; brews/potions use "Drink". Try Eat then fall back to Drink. */
    private void eat(String name)
    {
        if (name == null || name.isEmpty()) return;
        human.react();
        if (!Rs2Inventory.interact(name, "Eat"))
            Rs2Inventory.interact(name, "Drink");
    }

    // ---- states ------------------------------------------------------
    private void handleIdle()
    {
        prayer.clearOverheads();
        human.maybeMicroBreakBetweenWaves();
        handleDescend(); // the descend prompt usually sits in the idle gap
    }

    private void handleStandard(Rs2NpcModel doom)
    {
        if (doom == null) return;

        if (lastState != DoomState.STANDARD)
        {
            boolean deep = phase.getDelveLevel() >= 8;
            WorldPoint open = deep ? DoomTiles.B_NW : DoomTiles.B_NORTH;
            human.humanizedWalk(open, DoomState.STANDARD, hpPercent(),
                wp -> !rocks.shadowTiles().contains(wp));
            gear.equip(cfg.weaponMain());
            prayer.offensiveRanged();
        }

        keepAttacking(doom);
        human.react();
        prayer.protectAgainstIncoming();

        // Refund spec on the next kill: cast Death Charge on cooldown.
        spells.maybeDeathCharge(specPercent(), cfg.deathCharge());
    }

    private void handleMeleePunish(Rs2NpcModel doom)
    {
        if (doom == null) return;
        // Crystal halberd SPEC is the punish (halberd reach also keeps us off acid).
        // spec() equips first, then fires when energy allows; if it can't yet,
        // fall back to a normal hit so the charge still gets interrupted.
        human.react();
        if (!gear.spec(cfg.weaponPunish(), 30))
        {
            if (gear.isWearing(cfg.weaponPunish())) doom.interact("Attack");
        }
        prayer.protectAgainstIncoming();
    }

    private void handleRockThrow(Rs2NpcModel doom)
    {
        human.react();
        rocks.dodge(DoomTiles.D3);
        prayer.protectAgainstIncoming();
        if (doom != null && !rocks.playerOnShadow())
        {
            keepAttacking(doom);
        }
    }

    private void handleShockwave(Rs2NpcModel doom)
    {
        // Build an earthen shield: attacking two Volatile-earth NPCs spawns the
        // moving Earthen-shield NPC. First orb hit = destination, second = spawn.
        // Then stand on the shield until the shockwaves end.
        List<Rs2NpcModel> orbs = DoomQuery.withIds(DoomIds.NPC_VOLATILE_EARTH);
        Rs2NpcModel shield = DoomQuery.nearest(DoomIds.NPC_EARTHEN_SHIELD);

        if (shield != null)
        {
            // shield exists -> stand in it and keep DPS / prayer
            if (!Rs2Player.getWorldLocation().equals(shield.getWorldLocation()))
                Rs2Walker.walkFastCanvas(shield.getWorldLocation());
        }
        else if (!orbs.isEmpty())
        {
            // attack the orb nearest the priority white line first (destination),
            // then the loop will pick up the second on the next tick (spawn).
            // TODO refine: choose the two orbs giving the longest line that clears
            //              WHITE_LINE then D3 of acid; for now use nearest orb.
            human.react();
            orbs.get(0).interact("Attack");
        }

        prayer.protectAgainstIncoming();
        if (doom != null && shield != null) keepAttacking(doom);
    }

    private void handleShieldPhase(Rs2NpcModel doom)
    {
        if (doom == null) return;

        // Free heal: ancient gods sword spec during shield phase always heals.
        if (cfg.healSpecSword() && hpPercent() < 70)
        {
            if (gear.spec(cfg.weaponHealSpec(), 25))
            {
                gear.equip(cfg.weaponMain()); // swap back after the heal spec
                return;
            }
        }

        Rs2NpcModel grub = larvae.nextTarget(doom);
        if (grub != null)
        {
            LarvaHandler.Style st = larvae.styleOf(grub);
            String weapon = st == LarvaHandler.Style.MELEE ? cfg.weaponMeleeDemonbane()
                          : st == LarvaHandler.Style.MAGIC ? cfg.weaponMagicGrub()
                          : cfg.weaponMain();
            // Swap first; if a swap was issued this tick, attack next tick so the
            // weapon is actually equipped (matches the flick pattern in the log).
            if (gear.equip(weapon))
            {
                prayer.protectAgainstIncoming();
                return;
            }
            human.react();
            grub.interact("Attack");
        }
        else
        {
            gear.equip(cfg.weaponMagicGrub());
            doom.interact("Attack");
        }
        human.microJitter();
        prayer.protectAgainstIncoming();
    }

    private void handleCarPhase(Rs2NpcModel doom)
    {
        if (doom == null) return;

        // Blowpipe when close enough, otherwise the main bow for reach.
        int dist = Rs2Player.getWorldLocation().distanceTo(doom.getWorldLocation());
        if (cfg.blowpipeInCarPhase() && dist <= cfg.blowpipeRange())
        {
            if (gear.equip(cfg.weaponBlowpipe())) return; // swap this tick, attack next
        }
        else
        {
            if (gear.equip(cfg.weaponMain())) return;
        }

        if (cfg.rockblock() && phase.getDelveLevel() >= 6)
        {
            walkRockblock(doom);
        }
        else
        {
            transitionByEye();
        }

        keepAttacking(doom); // any attack resets the burrow charge
    }

    /** Walk D1 -> D2 -> D3 as the burrow eye (gfx 3415/3416) pops each time. */
    private void transitionByEye()
    {
        boolean eyeUp = !RockThrowHandler.graphicsOfType(DoomIds.GFX_BURROW_EYE).isEmpty()
                     || !RockThrowHandler.graphicsOfType(DoomIds.GFX_BURROW_EYE_2).isEmpty();
        WorldPoint me = Rs2Player.getWorldLocation();
        // first priority: be on/behind D1; then step out as the eye appears
        if (!DoomTiles.D1.contains(me) && !eyeUp)
            Rs2Walker.walkFastCanvas(DoomTiles.D1_LABEL);
        else if (eyeUp)
            Rs2Walker.walkFastCanvas(DoomTiles.D2_LABEL);
    }

    /** Rockblock (delve 6+): step gold -> cyan -> purple as each eye pops. */
    private void walkRockblock(Rs2NpcModel doom)
    {
        boolean eyeUp = !RockThrowHandler.graphicsOfType(DoomIds.GFX_BURROW_EYE).isEmpty()
                     || !RockThrowHandler.graphicsOfType(DoomIds.GFX_BURROW_EYE_2).isEmpty();
        WorldPoint me = Rs2Player.getWorldLocation();
        if (me.equals(DoomTiles.RB_1_GOLD) && eyeUp)        Rs2Walker.walkFastCanvas(DoomTiles.RB_2_CYAN);
        else if (me.equals(DoomTiles.RB_2_CYAN) && eyeUp)   Rs2Walker.walkFastCanvas(DoomTiles.RB_3_PURPLE);
        else if (!me.equals(DoomTiles.RB_1_GOLD) && !me.equals(DoomTiles.RB_2_CYAN)
                 && !me.equals(DoomTiles.RB_3_PURPLE))       Rs2Walker.walkFastCanvas(DoomTiles.RB_1_GOLD);
    }

    private void handleDescend()
    {
        prayer.clearOverheads();

        // The "Burrow hole" (57285) spawns after each delve.
        if (!Rs2GameObject.exists(DoomIds.OBJ_BURROW_HOLE)) return;

        SessionManager.Decision decision = session.decide(cfg, phase.getDelveLevel());
        if (decision == SessionManager.Decision.BANK)
        {
            handleBankTrip();
            return;
        }

        // Continue: Investigate the hole, then click the "Descend" dialog option.
        if (Rs2Widget.clickWidget("Descend"))
        {
            phase.setDelveLevel(phase.getDelveLevel() + 1);
            spells.reset(); // Death Charge cooldown resets effectively per delve kill
            human.pause(600, 200);
            return;
        }
        Rs2GameObject.interact(DoomIds.OBJ_BURROW_HOLE, "Investigate", 10);
    }

    /**
     * Bank trip: triggered by a unique drop, a hard delve cap, or low supplies.
     *
     * The CLAIM-TO-BANK part is verified and safe (lobby dialog only): Investigate
     * the hole, then click through "Claim and leave" -> "Confirm" -> "Bank-all" ->
     * "Leave", which banks the loot and drops you in the lobby. We click whichever
     * option is currently on screen, so the dialog walks itself across ticks.
     *
     * After leaving: if auto-banking is on, run the (experimental) resupply
     * routine; otherwise stop so you can resupply by hand.
     */
    private void handleBankTrip()
    {
        String why = session.uniqueDropped() ? "unique drop"
                   : (cfg.maxDelve() > 0 && phase.getDelveLevel() >= cfg.maxDelve()) ? "delve cap"
                   : "low supplies";

        // Walk the claim dialog if any of its options are up.
        if (Rs2Widget.clickWidget("Claim and leave")) return;
        if (Rs2Widget.clickWidget("Confirm"))         return;
        if (Rs2Widget.clickWidget("Bank-all"))        return;
        if (Rs2Widget.clickWidget("Leave"))           return;

        // Still at the hole and no dialog yet -> open it.
        if (Rs2GameObject.exists(DoomIds.OBJ_BURROW_HOLE)
            && Rs2Player.getWorldLocation().getRegionID() != DoomIds.LOBBY_REGION)
        {
            Rs2GameObject.interact(DoomIds.OBJ_BURROW_HOLE, "Investigate", 10);
            return;
        }

        // Loot is banked and we're out. Either resupply or stop.
        if (cfg.bankingEnabled() && bankRoutine.run(cfg, mainScheduledFuture))
        {
            // routine finished a full lap: restart from delve 1
            session.clearUnique();
            phase.setDelveLevel(1);
            return;
        }

        if (!cfg.bankingEnabled())
        {
            Microbot.log("Loot banked (" + why + "). Auto-resupply is off — resupply and restart manually.");
            shutdown();
        }
    }

    // ---- helpers -----------------------------------------------------
    private void keepAttacking(Rs2NpcModel doom)
    {
        if (doom == null) return;
        if (!Rs2Player.isInteracting())
        {
            doom.interact("Attack");
        }
    }

    private int hpPercent()
    {
        int real = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
        int cur  = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        return real == 0 ? 100 : (int) Math.round(100.0 * cur / real);
    }

    private int prayerPercent()
    {
        int real = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);
        int cur  = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
        return real == 0 ? 100 : (int) Math.round(100.0 * cur / real);
    }

    private int specPercent()
    {
        return Microbot.getClient().getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT) / 10; // 0-1000 -> %
    }

    @Override
    public void shutdown()
    {
        human.shutdown();
        super.shutdown();
    }
}
