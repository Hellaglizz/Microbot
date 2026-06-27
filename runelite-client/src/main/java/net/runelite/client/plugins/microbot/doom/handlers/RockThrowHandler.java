package net.runelite.client.plugins.microbot.doom.handlers;

import net.runelite.api.GameObject;
import net.runelite.api.GraphicsObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.doom.data.DoomIds;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.List;

/**
 * Rock Throw: the Doom lobs a rock that explodes after 7 ticks into falling
 * rocks. One always lands on the tile you stood on at explosion (and becomes a
 * permanent rock); the rest land on "looming shadow" tiles. Standing on a
 * shadow when they land = 15-20 damage.
 *
 * Strategy implemented:
 *   - Detect shadows (GFX_ROCK_SHADOW) and the set of shadow tiles.
 *   - Pick a safe tile = nearest reachable tile NOT under a shadow, preferring
 *     tiles already protected by an existing landed rock.
 *   - On delve 8 the caller waits for the SECOND rock before re-praying;
 *     the first rock's shadows are reused as cover for the second.
 */
public class RockThrowHandler
{
    // ---- shared scene queries (used by PhaseTracker too) -------------

    public static List<GraphicsObject> graphicsOfType(int id)
    {
        List<GraphicsObject> out = new ArrayList<>();
        if (id == -1) return out;
        for (GraphicsObject go : Microbot.getClient().getGraphicsObjects())
        {
            if (go.getId() == id) out.add(go);
        }
        return out;
    }

    public static List<GameObject> objectsOfType(int id)
    {
        if (id == -1) return new ArrayList<>();
        return Rs2GameObject.getGameObjects(id);
    }

    // ---- rock logic --------------------------------------------------

    /** World tiles currently covered by a falling-rock shadow. */
    public List<WorldPoint> shadowTiles()
    {
        List<WorldPoint> tiles = new ArrayList<>();
        for (GraphicsObject go : graphicsOfType(DoomIds.GFX_ROCK_SHADOW))
        {
            LocalPoint lp = go.getLocation();
            if (lp != null)
            {
                tiles.add(WorldPoint.fromLocal(Microbot.getClient(), lp));
            }
        }
        return tiles;
    }

    /** World tiles occupied by permanent landed rocks (usable as cover / rockblock). */
    public List<WorldPoint> rockTiles()
    {
        List<WorldPoint> tiles = new ArrayList<>();
        for (GameObject go : objectsOfType(DoomIds.OBJ_LANDED_ROCK))
        {
            tiles.add(go.getWorldLocation());
        }
        return tiles;
    }

    public boolean playerOnShadow()
    {
        WorldPoint me = Rs2Player.getWorldLocation();
        return shadowTiles().contains(me);
    }

    /**
     * Choose the nearest reachable tile that is not under a shadow. Prefers tiles
     * behind / adjacent to an existing rock so the next mechanic is also covered.
     * Returns null if the current tile is already safe.
     */
    public WorldPoint pickDodgeTile(List<WorldPoint> candidatePreference)
    {
        if (!playerOnShadow()) return null;

        List<WorldPoint> shadows = shadowTiles();

        // 1) preferred meta tiles first (e.g. behind a rock, D-line tiles)
        if (candidatePreference != null)
        {
            for (WorldPoint wp : candidatePreference)
            {
                if (!shadows.contains(wp)) return wp;
            }
        }

        // 2) otherwise scan a small radius around the player for the closest safe tile
        WorldPoint me = Rs2Player.getWorldLocation();
        WorldPoint best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++)
        {
            for (int dy = -3; dy <= 3; dy++)
            {
                WorldPoint wp = new WorldPoint(me.getX() + dx, me.getY() + dy, me.getPlane());
                if (shadows.contains(wp)) continue;
                int dist = me.distanceTo(wp);
                if (dist > 0 && dist < bestDist)
                {
                    bestDist = dist;
                    best = wp;
                }
            }
        }
        return best;
    }

    /** Walk to the chosen dodge tile if needed. Returns true if a move was issued. */
    public boolean dodge(List<WorldPoint> candidatePreference)
    {
        WorldPoint target = pickDodgeTile(candidatePreference);
        if (target == null) return false;
        Rs2Walker.walkFastCanvas(target);
        return true;
    }
}
