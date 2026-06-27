package net.runelite.client.plugins.microbot.doom.handlers;

import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.doom.enums.DoomState;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Humanization layer.
 *
 * Design rule for a one-shot boss: imperfection is COSMETIC only. We never
 * introduce a wrong *movement* click, because at Doom a single mis-step into
 * acid / a rock shadow, or a late prayer, is lethal. "Non-deadly misclicks"
 * here means camera nudges, idle mouse drift, examines, and reaction jitter —
 * never a click that changes where the character stands or which prayer is up.
 *
 * Everything is gated behind {@link #safeWindow}: idle behaviour only fires in
 * STANDARD / SHIELD_PHASE, at healthy HP, with nothing incoming. During
 * ROCK_THROW, SHOCKWAVE, MELEE_PUNISH and CAR_PHASE the bot plays "tryhard" —
 * tight reactions, zero fluff.
 */
public class Humanizer
{
    /** How often, per safe tick, a cosmetic idle action may fire (0.0-1.0). */
    private double idleChance = 0.06;
    /** How often a safe camera rotation fires within idle behaviour. */
    private double cameraChance = 0.5;
    /** How often a micro-break is *considered* between waves. */
    private boolean microBreaksBetweenWaves = true;
    /** Opt-in wrong-tile-then-correct movement (off by default; safe-window only). */
    private boolean wrongClicks = false;
    private double wrongClickChance = 0.04;

    public void setIdleChance(double c)   { idleChance = clamp(c); }
    public void setCameraChance(double c) { cameraChance = clamp(c); }
    public void setMicroBreaks(boolean b) { microBreaksBetweenWaves = b; }
    public void setWrongClicks(boolean on, double chance) { wrongClicks = on; wrongClickChance = clamp(chance); }

    /** Call once when the script starts. Turns on the framework's antiban stack. */
    public void init()
    {
        Rs2Antiban.activateAntiban();
        Rs2AntibanSettings.naturalMouse = true;          // curved trajectories + overshoot
        Rs2AntibanSettings.moveMouseRandomly = true;     // idle drift
        Rs2AntibanSettings.simulateMistakes = true;      // framework-level minor fumbles
        Rs2AntibanSettings.simulateAttentionSpan = true; // engagement decays over a session
        Rs2AntibanSettings.actionCooldownActive = true;
        // Combat at a boss is high-intensity, near-constant input: tune accordingly
        Rs2Antiban.setActivity(Activity.GENERAL_COMBAT);
        Rs2Antiban.setActivityIntensity(ActivityIntensity.HIGH);
    }

    public void shutdown()
    {
        Rs2Antiban.deactivateAntiban();
    }

    // ------------------------------------------------------------------
    // Reaction timing — call IMMEDIATELY BEFORE a reactive action.
    // ------------------------------------------------------------------

    /**
     * Log-normal human reaction delay (~260 ms median, right-skewed, fatigue
     * applied). Use before prayer flicks, grub clicks, dodge clicks, etc., so
     * reactions don't snap to a fixed grid.
     */
    public void react()
    {
        Global.sleep(Rs2Random.reactionTime());
    }

    /** A gaussian dwell with explicit mean/spread, for non-reaction pauses. */
    public void pause(int meanMs, int stdMs)
    {
        Global.sleepGaussian(meanMs, stdMs);
    }

    /** Tiny jitter between two same-tick sub-actions (e.g. pray then re-target). */
    public void microJitter()
    {
        Global.sleep(Rs2Random.logNormalBounded(15, 90));
    }

    // ------------------------------------------------------------------
    // Safe-window gating
    // ------------------------------------------------------------------

    /** True only when it is safe to be cosmetically imperfect. */
    public boolean safeWindow(DoomState state, int hpPercent)
    {
        if (hpPercent < 60) return false;
        switch (state)
        {
            case STANDARD:
            case SHIELD_PHASE:
                return true;
            default:
                return false; // rock/shockwave/punish/car = no fluff
        }
    }

    // ------------------------------------------------------------------
    // Cosmetic idle behaviour (the "human-like" filler)
    // ------------------------------------------------------------------

    /**
     * Maybe perform one harmless idle action. Returns true if it did something,
     * so the caller can spend the rest of the tick normally. Never moves the
     * character and never touches prayers.
     */
    public boolean maybeIdle(DoomState state, int hpPercent, NPC doom)
    {
        if (!safeWindow(state, hpPercent)) return false;
        if (Rs2Random.between(0, 100) >= idleChance * 100) return false;

        double roll = Math.random();
        if (roll < cameraChance)
        {
            safeCameraNudge(doom);
        }
        else if (roll < cameraChance + 0.30)
        {
            Rs2Antiban.moveMouseRandomly(); // idle mouse drift, stays on canvas
        }
        else
        {
            Rs2Antiban.actionCooldown(); // framework-decided natural pause
        }
        return true;
    }

    /**
     * Rotate the camera by a small random amount, or re-centre on the boss.
     * Pure view change — input continues to work because clicks are world-space.
     */
    private void safeCameraNudge(NPC doom)
    {
        if (doom != null && Rs2Random.between(0, 100) < 35)
        {
            Rs2Camera.turnTo(doom); // re-focus, like a player keeping the boss centred
            return;
        }
        int current = Rs2Camera.getAngle();
        int delta = Rs2Random.between(-40, 40);
        Rs2Camera.setAngle((current + delta + 360) % 360);
    }

    /**
     * Between waves only: consider a longer micro-break. Returns true if a break
     * was taken (caller should re-evaluate state afterwards). Never called mid-fight.
     */
    public boolean maybeMicroBreakBetweenWaves()
    {
        if (!microBreaksBetweenWaves) return false;
        return Rs2Antiban.takeMicroBreakByChance();
    }

    // ------------------------------------------------------------------
    // Movement with optional wrong-tile-then-correct (opt-in).
    // ------------------------------------------------------------------

    /**
     * Walk to {@code intended}. If wrong-clicks are enabled AND we're in a safe
     * window AND the dice say so, first click a *verified-safe* neighbour of the
     * destination, pause briefly ("oops"), then click the real tile — the genuine
     * human fat-finger-and-correct pattern, without ever stepping somewhere lethal.
     *
     * {@code isSafe} is supplied by the caller (e.g. "not under a rock shadow and
     * not on acid blood"), so the wrong tile is never a hazard tile.
     */
    public void humanizedWalk(WorldPoint intended, DoomState state, int hp, Predicate<WorldPoint> isSafe)
    {
        if (wrongClicks
            && safeWindow(state, hp)
            && Rs2Random.between(0, 100) < wrongClickChance * 100)
        {
            WorldPoint wrong = pickSafeNeighbour(intended, isSafe);
            if (wrong != null)
            {
                Rs2Walker.walkFastCanvas(wrong);
                Global.sleep(Rs2Random.logNormalBounded(120, 380)); // notice + correct
                Rs2Walker.walkFastCanvas(intended);
                return;
            }
        }
        Rs2Walker.walkFastCanvas(intended);
    }

    /** A reachable, safe 8-neighbour of {@code centre}, or null if none qualifies. */
    private WorldPoint pickSafeNeighbour(WorldPoint centre, Predicate<WorldPoint> isSafe)
    {
        List<int[]> deltas = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                if (dx != 0 || dy != 0) deltas.add(new int[]{dx, dy});
        Collections.shuffle(deltas);

        for (int[] d : deltas)
        {
            WorldPoint wp = new WorldPoint(centre.getX() + d[0], centre.getY() + d[1], centre.getPlane());
            boolean safe = (isSafe == null) || isSafe.test(wp);
            if (safe && Rs2Walker.canReach(wp)) return wp;
        }
        return null;
    }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
}
