package net.runelite.client.plugins.microbot.corruptedgauntlet;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.corruptedgauntlet.data.GauntletIds;
import net.runelite.client.plugins.microbot.corruptedgauntlet.enums.GauntletState;
import net.runelite.client.plugins.microbot.corruptedgauntlet.handlers.BossHandler;
import net.runelite.client.plugins.microbot.corruptedgauntlet.handlers.GauntletNav;
import net.runelite.client.plugins.microbot.corruptedgauntlet.handlers.PrepHandler;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Full Corrupted Gauntlet runner: lobby -> prep (gather/craft) -> Hunllef -> loot
 * -> repeat. The state is DERIVED from the world each loop (region, NPCs,
 * inventory) so it self-corrects; handlers do the work.
 */
@Slf4j
public class CorruptedGauntletScript extends Script
{
    public static String version = "1.0.0";
    public static GauntletState state = GauntletState.IDLE;

    /**
     * True once we've crossed the Barrier into the Hunllef arena. This is the ONLY
     * thing that puts us in the BOSS state — the Hunllef NPC is loaded for the whole
     * instance (even ~14-17 tiles away through walls while we prep, in the same
     * region), so "a Hunllef exists" must never trigger the fight. Cleared in the
     * lobby / on finish / on death.
     */
    private boolean arenaEntered = false;

    public boolean run(CorruptedGauntletConfig config)
    {
        this.cfg = config;
        BossHandler.reset();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!Microbot.isLoggedIn() || !super.run()) return;

                state = deriveState();
                switch (state)
                {
                    case LOBBY:       startGauntlet(); break;
                    case PREP_GATHER: PrepHandler.gather(config); break;
                    case PREP_CRAFT:  PrepHandler.craft(config); break;
                    case ENTER_ARENA: enterArena(); break;
                    case BOSS:        BossHandler.fight(config); break;
                    case FINISH:      finish(config); break;
                    case DEAD:        onDeath(config); break;
                    default: break;
                }
            }
            catch (Exception ex)
            {
                log.error("[CG] loop error", ex);
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
        return true;
    }

    // ------------------------------------------------------------------
    // State derivation
    // ------------------------------------------------------------------
    private GauntletState deriveState()
    {
        if (Rs2Player.getWorldLocation() == null) return GauntletState.IDLE;

        if (BossHandler.dead()) return GauntletState.DEAD;

        boolean inLobby = Rs2Player.getWorldLocation().getRegionID() == GauntletIds.REGION_LOBBY;
        if (inLobby)
        {
            arenaEntered = false;
            return GauntletState.LOBBY;
        }

        // Fallback for starting the script already inside the arena: we're right next
        // to the boss (prep keeps you 14+ tiles away even in the same region).
        if (!arenaEntered && BossHandler.nearBoss(6))
            arenaEntered = true;

        // Once across the Barrier: fight until the Hunllef is dead, then finish.
        if (arenaEntered)
            return BossHandler.bossPresent() ? GauntletState.BOSS : GauntletState.FINISH;

        // Otherwise we're in the prep maze (the Hunllef is loaded but walled off).
        if (PrepHandler.gatherDone(config()) && PrepHandler.ready(config()))
            return GauntletState.ENTER_ARENA;
        if (PrepHandler.gatherDone(config()))
            return GauntletState.PREP_CRAFT;
        return GauntletState.PREP_GATHER;
    }

    // Stashed in run() so deriveState() can read the gather/craft quotas.
    private CorruptedGauntletConfig cfg;
    private CorruptedGauntletConfig config() { return cfg; }

    // ------------------------------------------------------------------
    // State actions
    // ------------------------------------------------------------------
    private void startGauntlet()
    {
        Microbot.status = "[CG] entering Corrupted Gauntlet";
        // Verified action from trace: "Enter-corrupted" on "The Gauntlet".
        if (!Rs2GameObject.interact(GauntletIds.OBJ_ENTRANCE, "Enter-corrupted")
            && !Rs2GameObject.interact("The Gauntlet", "Enter-corrupted")
            && !Rs2GameObject.interact(GauntletIds.OBJ_ENTRANCE))
            Microbot.status = "[CG] entrance not found (stand on the corrupted platform)";
        sleepUntil(() -> Rs2Player.getWorldLocation() != null
            && Rs2Player.getWorldLocation().getRegionID() != GauntletIds.REGION_LOBBY, 6000);
    }

    private void enterArena()
    {
        Microbot.status = "[CG] entering arena";
        // Pray protection (boss opens with ranged) before crossing the barrier.
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
        GauntletNav.enterArena();
        // Confirm we actually crossed in (now next to the boss), then commit to BOSS.
        if (sleepUntil(() -> BossHandler.nearBoss(8), 6000))
            arenaEntered = true;
    }

    private void finish(CorruptedGauntletConfig config)
    {
        Microbot.status = "[CG] looting & leaving";
        Rs2Prayer.disableAllPrayers();
        // Loot the reward chest if present.
        Rs2GameObject.interact(GauntletIds.OBJ_CHEST_OPEN, "Take");
        Rs2GameObject.interact(GauntletIds.OBJ_CHEST_CLOSED, "Open");
        Rs2GroundItem.loot("Corrupted shards", 6);
        // Leave the dungeon back to the lobby.
        if (!Rs2GameObject.interact(GauntletIds.OBJ_BLOCKADE_ESCAPE, "Quick-pass"))
            Rs2GameObject.interact(GauntletIds.OBJ_LOBBY_EXIT, "Exit");
        arenaEntered = false;
        sleepUntil(() -> Rs2Player.getWorldLocation() != null
            && Rs2Player.getWorldLocation().getRegionID() == GauntletIds.REGION_LOBBY, 8000);
        if (!config.autoRestart()) shutdown();
    }

    private void onDeath(CorruptedGauntletConfig config)
    {
        Microbot.status = "[CG] died";
        Rs2Prayer.disableAllPrayers();
        if (config.stopOnDeath())
        {
            Microbot.status = "[CG] stopped on death";
            shutdown();
        }
        // else: fall through; next loop will detect lobby and restart if enabled.
        arenaEntered = false;
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
        state = GauntletState.IDLE;
        arenaEntered = false;
    }
}
