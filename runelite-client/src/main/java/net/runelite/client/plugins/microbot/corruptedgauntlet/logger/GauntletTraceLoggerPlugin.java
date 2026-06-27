package net.runelite.client.plugins.microbot.corruptedgauntlet.logger;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.GraphicsObject;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Projectile;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.corruptedgauntlet.data.GauntletIds;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Corrupted Gauntlet FULL TRACE logger. No automation — records only.
 *
 * Same philosophy as the Doom Trace Logger: emit a complete tick-by-tick TRACE
 * so an example run can be reconstructed and turned into a plugin. The Gauntlet
 * has two very different halves and this captures both:
 *
 *  PREP PHASE
 *   - Resource node spawns/despawns + deplete transitions (rock/tree/pond/herb/
 *     fibre), the singing bowl, range, sink, arena blockade.
 *   - EVERY inventory delta in both directions: resources gathered, items
 *     crafted/cooked, supplies consumed — the full gathering/crafting routine.
 *   - Corrupted creatures + demi-bosses (bear/dragon/dark beast) per tick.
 *
 *  BOSS PHASE (Corrupted Hunllef)
 *   - Hunllef per tick: tile/trajectory, animation, target, HP ratio, and the
 *     DERIVED ATTACK STYLE from its NPC id (9035 melee / 9036 ranged / 9037
 *     magic) — i.e. which protection prayer you should be on.
 *   - A hunllef_attack event with a running attack counter (for the 6-attack
 *     weapon-swap rule) on each new Hunllef projectile.
 *   - Tornado NPCs per tick (dodge path), projectiles -> style + impact tick,
 *     graphics objects (crystal telegraphs) and the danger floor tiles.
 *
 *  ALWAYS
 *   - YOU every tick: prev->new tile + delta/direction, walk/run, click
 *     destination, animation, equipped weapon, target, prayers, hp/pray/spec/run.
 *   - prayer_switch on the exact tick your prayer set changes; gear swaps;
 *     your clicks; hitsplats; chat.
 *
 * Output under .../corruptedgauntlet/logs/:
 *   - trace-<timestamp>.jsonl  : one structured record per line
 *   - trace-<timestamp>.txt    : a readable per-tick narrative (optional)
 */
@PluginDescriptor(
    name = "CGauntlet Trace Logger",
    description = "Full per-tick trace of you, the Hunllef, tornadoes, creatures, resources and projectiles for Corrupted Gauntlet plugin dev.",
    tags = {"microbot", "gauntlet", "corrupted", "hunllef", "logger", "trace", "dev"},
    enabledByDefault = false
)
@Slf4j
public class GauntletTraceLoggerPlugin extends Plugin
{
    /** Bump when the trace schema changes so old captures aren't confused with new. */
    public static final int VERSION = 5;

    private static final Pattern GAUNTLET_NAME = Pattern.compile("(?i)hunllef|corrupted|crystalline|tornado|crystal");

    private static final int SPEC_VARP = VarPlayer.SPECIAL_ATTACK_PERCENT;

    private static final Pattern SUPPLY_NAME = Pattern.compile(
        "(?i)egniol|potion|fish|food|paddlefish");

    @Inject private Client client;
    @Inject private GauntletTraceLoggerConfig config;
    @Inject private ItemManager itemManager;

    private BufferedWriter json;
    private BufferedWriter text;

    // Movement memory (prev world tile, keyed by NPC index; single field for the player).
    private WorldPoint prevPlayerTile;
    private final Map<Integer, WorldPoint> prevNpcTile = new HashMap<>();
    private Set<Prayer> prevPrayers = EnumSet.noneOf(Prayer.class);

    // Inventory baseline (both-direction deltas).
    private final Map<Integer, Integer> lastInv = new HashMap<>();
    private boolean invInit = false;

    // Equipment (gear swap) baseline: slot -> itemId.
    private final Map<Integer, Integer> lastEquip = new HashMap<>();
    private boolean equipInit = false;

    // Hunllef state: attack counting + style tracking.
    private final Set<Projectile> seenProjectiles =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private int hunllefAttackCount = 0;
    // What the Hunllef is PROTECTING (from its id) — flips ~every 24-36t ("6 off-
    // prayer hits you land"); drives YOUR weapon swap (hit a style it isn't
    // protecting). The overhead sprite ids are the same signal (verified to flip in
    // lockstep); logged raw so the 440:x -> melee/ranged/magic mapping can be
    // decoded from a capture.
    private String prevHunllefProtection;
    private String prevHunllefOverhead;
    // The Hunllef's ATTACK style (from its projectile id) — flips every 4 attacks
    // (~20t, starts ranged); drives YOUR protection prayer. Independent of what it
    // is protecting.
    private String prevHunllefAttackStyle;

    // Per-tick narrative buffer for events fired during the tick.
    private final List<String> pendingTxtEvents = new ArrayList<>();

    @Provides
    GauntletTraceLoggerConfig provideConfig(ConfigManager cm)
    {
        return cm.getConfig(GauntletTraceLoggerConfig.class);
    }

    @Override
    protected void startUp()
    {
        try
        {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            File dir = new File("C:\\Users\\Hellenism\\IdeaProjects\\Microbot\\runelite-client\\src\\main\\java\\net\\runelite\\client\\plugins\\microbot\\corruptedgauntlet\\logs");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            json = new BufferedWriter(new FileWriter(new File(dir, "trace-" + ts + ".jsonl"), true));
            if (config.writeText())
                text = new BufferedWriter(new FileWriter(new File(dir, "trace-" + ts + ".txt"), true));
            json("meta", "traceVersion", VERSION);
            writeText("# CGauntlet trace schema version: " + VERSION + System.lineSeparator());
            log.info("[CGauntletTrace] writing trace-{}.jsonl{} (schema v{})",
                ts, text != null ? " (+ .txt)" : "", VERSION);
        }
        catch (IOException e)
        {
            log.error("[CGauntletTrace] could not open log files", e);
        }
    }

    @Override
    protected void shutDown()
    {
        close(json); json = null;
        close(text); text = null;
        prevNpcTile.clear();
        lastInv.clear();
        lastEquip.clear();
        seenProjectiles.clear();
        invInit = false;
        equipInit = false;
        hunllefAttackCount = 0;
        prevHunllefProtection = null;
        prevHunllefOverhead = null;
        prevHunllefAttackStyle = null;
        prevPlayerTile = null;
        prevPrayers = EnumSet.noneOf(Prayer.class);
        pendingTxtEvents.clear();
    }

    // ------------------------------------------------------------------
    // Classification helpers
    // ------------------------------------------------------------------
    private static boolean isHunllef(NPC npc)
    {
        if (npc == null) return false;
        int id = npc.getId();
        for (int h : GauntletIds.HUNLLEF_ALL) if (h == id) return true;
        return id == GauntletIds.HUNLLEF_MELEE_NORMAL || id == GauntletIds.HUNLLEF_RANGED_NORMAL
            || id == GauntletIds.HUNLLEF_MAGIC_NORMAL || id == GauntletIds.HUNLLEF_DEATH_NORMAL;
    }

    private static boolean isTornado(NPC npc)
    {
        return npc != null
            && (npc.getId() == GauntletIds.HUNLLEF_TORNADO || npc.getId() == GauntletIds.HUNLLEF_TORNADO_NORMAL);
    }

    private static boolean isGauntletNpc(NPC npc)
    {
        if (npc == null) return false;
        if (GauntletIds.ALL_NPCS.contains(npc.getId())) return true;
        return npc.getName() != null && GAUNTLET_NAME.matcher(npc.getName()).find();
    }

    /**
     * The Corrupted Hunllef NPC is loaded for the WHOLE instance (it sits in its
     * arena behind the barrier the entire time you prep), so mere presence is a
     * useless phase signal. "In the fight" == you are in the same region as the
     * Hunllef and within its arena footprint (a few tiles), which is only true
     * once you've crossed the blockade. Returns the player->Hunllef distance, or
     * -1 if no Hunllef / not engaged.
     */
    private int hunllefFightDist()
    {
        Player me = client.getLocalPlayer();
        if (me == null || me.getWorldLocation() == null) return -1;
        WorldPoint myLoc = me.getWorldLocation();        // instanced coords
        int myRegion = myLoc.getRegionID();
        int best = -1;
        for (NPC npc : client.getNpcs())
        {
            if (!isHunllef(npc)) continue;
            WorldArea wa = npc.getWorldArea();
            WorldPoint hp = npc.getWorldLocation();       // instanced coords (NOT iwp/template)
            if (wa == null || hp == null) continue;
            // Compare in the SAME (instanced) space: the arena is its own dynamic
            // region, distinct from every prep room, so same-region == in the fight.
            // (Earlier bug: compared npc template region to player instanced region,
            // which never matches inside an instance.)
            if (hp.getRegionID() != myRegion) continue;
            int d = wa.distanceTo(myLoc);
            if (best < 0 || d < best) best = d;
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Spawn / despawn
    // ------------------------------------------------------------------
    @Subscribe
    public void onNpcSpawned(NpcSpawned e)
    {
        NPC npc = e.getNpc();
        if (!isGauntletNpc(npc)) return;
        WorldPoint at = iwp(npc);
        json("npc_spawn", "id", npc.getId(), "idx", npc.getIndex(),
            "name", q(npc.getName()), "tile", wp(at),
            "protects", q(GauntletIds.hunllefProtection(npc.getId())));
        txtEvent(String.format("  + spawn  %-22s id=%d idx=%d %s",
            npc.getName(), npc.getId(), npc.getIndex(), tileStr(at)));
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned e)
    {
        NPC npc = e.getNpc();
        if (!isGauntletNpc(npc)) return;
        prevNpcTile.remove(npc.getIndex());
        json("npc_despawn", "id", npc.getId(), "idx", npc.getIndex(), "name", q(npc.getName()));
        txtEvent(String.format("  - despawn %-22s id=%d idx=%d", npc.getName(), npc.getId(), npc.getIndex()));
    }

    // ------------------------------------------------------------------
    // Resource / scenery objects
    // ------------------------------------------------------------------
    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned e)
    {
        if (!config.logResources()) return;
        logObject("obj_spawn", e.getGameObject());
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned e)
    {
        if (!config.logResources()) return;
        logObject("obj_despawn", e.getGameObject());
    }

    private void logObject(String type, net.runelite.api.GameObject go)
    {
        try
        {
            int id = go.getId();
            boolean known = GauntletIds.RESOURCE_OBJS.contains(id);
            net.runelite.api.ObjectComposition def = client.getObjectDefinition(id);
            String name = def != null ? def.getName() : null;
            String actions = "";
            if (def != null && def.getActions() != null)
                actions = String.join("|", java.util.Arrays.stream(def.getActions())
                    .filter(a -> a != null && !a.isEmpty()).toArray(String[]::new));
            String low = ((name == null ? "" : name) + " " + actions).toLowerCase();
            // Only emit if it's a known gauntlet object or its name/actions look relevant —
            // otherwise the floor/wall scenery would drown the trace.
            boolean relevant = known
                || low.contains("corrupt") || low.contains("crystal") || low.contains("phren")
                || low.contains("grym") || low.contains("linum") || low.contains("fish")
                || low.contains("singing") || low.contains("range") || low.contains("pump")
                || low.contains("water") || low.contains("barrier") || low.contains("node")
                || low.contains("deposit") || low.contains("bark") || low.contains("ore");
            if (!relevant) return;
            json(type, "id", id, "name", q(name), "actions", q(actions),
                "tile", wp(go.getLocalLocation()));
            txtEvent(String.format("  # %s %d '%s' [%s] %s",
                type.endsWith("spawn") ? (type.contains("de") ? "obj-" : "obj+") : "obj",
                id, name, actions, tileStr(iwp(go.getLocalLocation()))));
        }
        catch (Exception ignored) {}
    }

    // ------------------------------------------------------------------
    // Your clicks
    // ------------------------------------------------------------------
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked e)
    {
        if (!config.logMenuClicks()) return;
        // If the click was on an interface, record the widget id (group<<16|child),
        // its text and any item id — this is how the crafting/cooking/potion menus
        // (singing bowl etc) are captured so the prep can be automated via Rs2Widget.
        Widget w = e.getWidget();
        int wid = w != null ? w.getId() : -1;
        int wgroup = wid >= 0 ? (wid >> 16) : -1;
        int wchild = wid >= 0 ? (wid & 0xFFFF) : -1;
        String wtext = w != null ? w.getText() : null;
        int witem = w != null ? w.getItemId() : -1;
        json("click",
            "option", q(e.getMenuOption()), "target", q(e.getMenuTarget()),
            "action", q(String.valueOf(e.getMenuAction())),
            "id", e.getId(), "p0", e.getParam0(), "p1", e.getParam1(),
            "wid", wid, "wgroup", wgroup, "wchild", wchild,
            "wtext", q(wtext), "witem", witem);
        String tgt = e.getMenuTarget() == null ? "" : e.getMenuTarget().replaceAll("<[^>]*>", "");
        txtEvent(String.format("  > click  %s %s%s", e.getMenuOption(), tgt,
            wgroup >= 0 ? "  [widget " + wgroup + "." + wchild
                + (wtext != null && !wtext.isEmpty() ? " '" + wtext.replaceAll("<[^>]*>", "") + "'" : "") + "]"
                : "").trim());
    }

    // ------------------------------------------------------------------
    // Interfaces opening — which group id is the singing bowl / cook / potion UI
    // ------------------------------------------------------------------
    @Subscribe
    public void onWidgetLoaded(WidgetLoaded e)
    {
        if (!config.logWidgets()) return;
        json("widget_open", "group", e.getGroupId());
        txtEvent("  [ interface group " + e.getGroupId() + " opened");
    }

    // ------------------------------------------------------------------
    // Hitsplats
    // ------------------------------------------------------------------
    @Subscribe
    public void onHitsplatApplied(HitsplatApplied e)
    {
        if (!config.logHitsplats()) return;
        Actor a = e.getActor();
        boolean onMe = a == client.getLocalPlayer();
        if (!onMe && !(a instanceof NPC && isGauntletNpc((NPC) a))) return;
        String who = onMe ? "player" : a.getName();
        json("hitsplat", "on", q(who), "amount", e.getHitsplat().getAmount(),
            "hstype", e.getHitsplat().getHitsplatType());
        txtEvent(String.format("  * hit %d on %s", e.getHitsplat().getAmount(), who));
    }

    // ------------------------------------------------------------------
    // Graphics objects — telegraphs
    // ------------------------------------------------------------------
    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated e)
    {
        if (!config.logGraphics()) return;
        GraphicsObject go = e.getGraphicsObject();
        json("gfx", "id", go.getId(), "tile", wp(go.getLocation()));
        txtEvent(String.format("  ~ gfx %d %s", go.getId(),
            tileStr(WorldPoint.fromLocalInstance(client, go.getLocation()))));
    }

    // ------------------------------------------------------------------
    // Inventory + equipment
    // ------------------------------------------------------------------
    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged e)
    {
        int id = e.getContainerId();
        if (config.logInventory() && id == InventoryID.INVENTORY.getId())
            diffInventory(e.getItemContainer());
        else if (config.logEquipment() && id == InventoryID.EQUIPMENT.getId())
            diffEquipment(e.getItemContainer());
    }

    /** Both-direction inventory deltas: gather (+), craft/cook (+/-), consume (-). */
    private void diffInventory(ItemContainer c)
    {
        Map<Integer, Integer> now = new HashMap<>();
        for (Item it : c.getItems())
            if (it.getId() > 0) now.merge(it.getId(), it.getQuantity(), Integer::sum);

        if (!invInit)
        {
            lastInv.clear();
            lastInv.putAll(now);
            invInit = true;
            return;
        }

        Set<Integer> ids = new java.util.HashSet<>();
        ids.addAll(now.keySet());
        ids.addAll(lastInv.keySet());
        for (int itemId : ids)
        {
            int delta = now.getOrDefault(itemId, 0) - lastInv.getOrDefault(itemId, 0);
            if (delta == 0) continue;
            String name = itemName(itemId);
            json("inv", "id", itemId, "name", q(name), "delta", delta, "now", now.getOrDefault(itemId, 0));
            txtEvent(String.format("  $ inv %s%d %s", delta > 0 ? "+" : "", delta, name));
        }
        lastInv.clear();
        lastInv.putAll(now);
    }

    /** Gear swaps: per-slot from -> to (the weapon rotation). */
    private void diffEquipment(ItemContainer c)
    {
        Item[] arr = c.getItems();
        Map<Integer, Integer> now = new HashMap<>();
        for (int slot = 0; slot < arr.length; slot++)
            if (arr[slot] != null && arr[slot].getId() > 0) now.put(slot, arr[slot].getId());

        if (!equipInit)
        {
            lastEquip.clear();
            lastEquip.putAll(now);
            equipInit = true;
            return;
        }

        Set<Integer> slots = new java.util.HashSet<>();
        slots.addAll(now.keySet());
        slots.addAll(lastEquip.keySet());
        for (int slot : slots)
        {
            int cur = now.getOrDefault(slot, -1);
            int prev = lastEquip.getOrDefault(slot, -1);
            if (cur == prev) continue;
            String fromN = prev > 0 ? itemName(prev) : null;
            String toN = cur > 0 ? itemName(cur) : null;
            json("equip", "slot", slot, "from", q(fromN), "to", q(toN));
            txtEvent(String.format("  = equip slot%d %s -> %s", slot, fromN, toN));
        }
        lastEquip.clear();
        lastEquip.putAll(now);
    }

    private String weaponName()
    {
        ItemContainer eq = client.getItemContainer(InventoryID.EQUIPMENT);
        if (eq == null) return null;
        Item w = eq.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
        return (w != null && w.getId() > 0) ? itemName(w.getId()) : null;
    }

    // ------------------------------------------------------------------
    // Chat
    // ------------------------------------------------------------------
    @Subscribe
    public void onChatMessage(ChatMessage e)
    {
        if (!config.logChat()) return;
        String msg = e.getMessage();
        if (msg == null) return;
        json("chat", "ctype", q(String.valueOf(e.getType())), "msg", q(msg));
        txtEvent("  \" " + msg.replaceAll("<[^>]*>", ""));
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged e)
    {
        if (e.getGameState() == GameState.LOADING || e.getGameState() == GameState.LOGIN_SCREEN)
        {
            prevNpcTile.clear();
            prevPlayerTile = null;
            invInit = false;
            equipInit = false;
            seenProjectiles.clear();
        }
    }

    // ------------------------------------------------------------------
    // Per-tick trace — fires last in a tick, groups the events above.
    // ------------------------------------------------------------------
    @Subscribe
    public void onGameTick(GameTick e)
    {
        int hunDist = hunllefFightDist();
        // "In the fight" == you are in the Hunllef's room (its arena is its own
        // dynamic region, distinct from every prep room). hunDist>=0 already means
        // same region; no distance cap needed (you can be in a far corner).
        boolean boss = hunDist >= 0;
        StringBuilder block = text != null ? new StringBuilder() : null;
        if (block != null)
            block.append(String.format("T%-8d %s ──────────────────────────────%n",
                client.getTickCount(), boss ? "[BOSS]" : "[PREP]"));

        json("phase", "boss", boss, "hunDist", hunDist, "region", regionId());

        if (config.logPlayer())         tracePlayer(block);
        if (config.logPrayerSwitches()) tracePrayerSwitch(block);
        traceNpcs(block);
        if (config.logProjectiles())    traceProjectiles(block);

        if (block != null)
        {
            for (String ev : pendingTxtEvents) block.append(ev).append(System.lineSeparator());
            writeText(block.toString());
        }
        pendingTxtEvents.clear();
        flush();
    }

    private void tracePlayer(StringBuilder block)
    {
        Player me = client.getLocalPlayer();
        if (me == null) return;
        WorldPoint tile = iwp(me);
        if (tile == null) return;
        int dx = prevPlayerTile != null ? tile.getX() - prevPlayerTile.getX() : 0;
        int dy = prevPlayerTile != null ? tile.getY() - prevPlayerTile.getY() : 0;
        int dist = Math.max(Math.abs(dx), Math.abs(dy));
        String move = dist == 0 ? "idle" : (dist >= 2 ? "run" : "walk");

        WorldPoint dest = destTile();
        Actor target = me.getInteracting();
        String prayers = activePrayerString();
        String weapon = weaponName();

        json("player",
            "tile", wp(tile),
            "dx", dx, "dy", dy, "dir", q(dir(dx, dy)), "move", q(move),
            "dest", wp(destLocal()),
            "anim", me.getAnimation(),
            "weapon", q(weapon),
            "target", q(target != null ? target.getName() : null),
            "prayers", q(prayers),
            "hp", boosted(Skill.HITPOINTS), "hpMax", real(Skill.HITPOINTS),
            "pray", boosted(Skill.PRAYER), "prayMax", real(Skill.PRAYER),
            "spec", client.getVarpValue(SPEC_VARP) / 10,
            "run", client.getEnergy() / 100);

        if (block != null)
        {
            block.append(String.format(" ME    %-15s %-5s %-5s anim=%-5d hp=%d/%d pr=%d sp=%d run=%d%s%s",
                tileStr(tile), move, dist == 0 ? "" : dir(dx, dy) + "(" + sgn(dx) + "," + sgn(dy) + ")",
                me.getAnimation(),
                boosted(Skill.HITPOINTS), real(Skill.HITPOINTS),
                boosted(Skill.PRAYER), client.getVarpValue(SPEC_VARP) / 10, client.getEnergy() / 100,
                weapon != null ? "  [" + weapon + "]" : "",
                target != null ? "  ->" + target.getName() : ""))
                .append(dest != null && !dest.equals(tile) ? "  dest" + tileStr(dest) : "")
                .append(System.lineSeparator());
            if (!prayers.isEmpty())
                block.append("        pray=[").append(prayers).append("]").append(System.lineSeparator());
        }
        prevPlayerTile = tile;
    }

    private void tracePrayerSwitch(StringBuilder block)
    {
        Set<Prayer> now = EnumSet.noneOf(Prayer.class);
        for (Prayer p : Prayer.values())
        {
            try { if (client.isPrayerActive(p)) now.add(p); }
            catch (Exception ignored) {}
        }
        if (now.equals(prevPrayers)) return;

        List<String> on = new ArrayList<>();
        List<String> off = new ArrayList<>();
        for (Prayer p : now) if (!prevPrayers.contains(p)) on.add(prettyPrayer(p));
        for (Prayer p : prevPrayers) if (!now.contains(p)) off.add(prettyPrayer(p));

        json("prayer_switch", "on", q(String.join("|", on)), "off", q(String.join("|", off)),
            "active", q(activePrayerString()));
        if (block != null)
            block.append(String.format("  ^ prayer %s%s%n",
                on.isEmpty() ? "" : "+" + String.join(",", on) + " ",
                off.isEmpty() ? "" : "-" + String.join(",", off)));
        prevPrayers = now;
    }

    private void traceNpcs(StringBuilder block)
    {
        for (NPC npc : client.getNpcs())
        {
            boolean hun = isHunllef(npc);
            boolean tor = isTornado(npc);
            boolean creature = !hun && !tor && isGauntletNpc(npc);

            if (hun && !config.logHunllef()) continue;
            if (tor && !config.logTornadoes()) continue;
            if (creature && !config.logCreatures()) continue;
            if (!hun && !tor && !creature) continue;

            WorldPoint tile = iwp(npc);
            if (tile == null) continue;
            WorldPoint prev = prevNpcTile.get(npc.getIndex());
            int dx = prev != null ? tile.getX() - prev.getX() : 0;
            int dy = prev != null ? tile.getY() - prev.getY() : 0;
            int dist = Math.max(Math.abs(dx), Math.abs(dy));
            Actor target = npc.getInteracting();
            // The id == the style the Hunllef is PROTECTING (drives YOUR weapon),
            // NOT its attack style (that comes from its projectile -> your prayer).
            String protects = GauntletIds.hunllefProtection(npc.getId());

            int size = -1, pdist = -1;
            WorldArea wa = npc.getWorldArea();
            if (wa != null)
            {
                size = wa.getWidth();
                Player me = client.getLocalPlayer();
                if (me != null && me.getWorldLocation() != null)
                    pdist = wa.distanceTo(me.getWorldLocation());
            }

            String kind = hun ? "hunllef" : tor ? "tornado" : "creature";
            String overhead = hun ? overheadStr(npc) : null;
            json("npc",
                "kind", q(kind),
                "id", npc.getId(), "idx", npc.getIndex(), "name", q(npc.getName()),
                "protects", q(protects),
                "overhead", q(overhead),
                "tile", wp(tile), "size", size, "pdist", pdist,
                "dx", dx, "dy", dy, "dir", q(dir(dx, dy)),
                "anim", npc.getAnimation(),
                "target", q(target != null ? target.getName() : null),
                "hpRatio", npc.getHealthRatio(), "hpScale", npc.getHealthScale());

            // Hunllef PROTECTION change == the tick YOUR weapon must swap to a style
            // it is no longer protecting. id (3-way melee/ranged/magic) and overhead
            // (verified to flip in lockstep) are the same signal; id is the reliable
            // one, overhead is the cross-check. Flips ~every 24-36t ("6 off-prayer
            // hits you land"), independent of its attack style.
            boolean protChanged = hun && protects != null && !"dead".equals(protects)
                && (!protects.equals(prevHunllefProtection)
                    || (overhead != null && !overhead.equals(prevHunllefOverhead)));
            if (protChanged)
            {
                json("hunllef_protection", "protects", q(protects), "prev", q(prevHunllefProtection),
                    "overhead", q(overhead), "prevOverhead", q(prevHunllefOverhead),
                    "attackNo", hunllefAttackCount);
                if (block != null)
                    block.append("  >> HUNLLEF PROTECTS ").append(protects)
                        .append(" [oh ").append(overhead).append("] -> attack a different style")
                        .append(System.lineSeparator());
                prevHunllefProtection = protects;
                prevHunllefOverhead = overhead;
            }

            if (block != null)
                block.append(String.format(" %-9s %-22s %-15s %-5s anim=%-5d%s%s%s%n",
                    kind.toUpperCase(), shortNpc(npc), tileStr(tile),
                    dist == 0 ? "" : dir(dx, dy) + "(" + sgn(dx) + "," + sgn(dy) + ")",
                    npc.getAnimation(),
                    protects != null ? "  prot=" + protects : "",
                    target != null ? "  ->" + target.getName() : "",
                    npc.getHealthScale() > 0 ? "  hp:" + npc.getHealthRatio() + "/" + npc.getHealthScale() : ""));

            prevNpcTile.put(npc.getIndex(), tile);
        }
    }

    private void traceProjectiles(StringBuilder block)
    {
        for (Projectile p : client.getProjectiles())
        {
            String style = GauntletIds.projectileStyle(p.getId());
            Actor t = p.getInteracting();
            Actor src = p.getSourceActor();
            String srcName = src != null ? src.getName() : null;
            int srcId = (src instanceof NPC) ? ((NPC) src).getId() : -1;
            // NB: getSourceActor() is null for Hunllef projectiles, so we identify
            // its attacks by projectile id, not by source. 1708 = magic attack,
            // 1712 = ranged attack (the two that drive the prayer/weapon flip);
            // 1714 (prayer-disable orb) and 1718 (crystals) are NOT counted.
            boolean isHunllefAttack =
                p.getId() == GauntletIds.PROJ_MAGIC  || p.getId() == GauntletIds.PROJ_MAGIC_NORMAL
             || p.getId() == GauntletIds.PROJ_RANGED || p.getId() == GauntletIds.PROJ_RANGED_NORMAL;

            // New projectile (same Projectile instance persists while in flight) ->
            // count it as a Hunllef attack (for the 4-attack style-flip cadence).
            boolean fresh = seenProjectiles.add(p);
            if (fresh && isHunllefAttack)
            {
                hunllefAttackCount++;
                json("hunllef_attack", "no", hunllefAttackCount, "style", q(style),
                    "projId", p.getId(), "target", q(t != null ? t.getName() : null));
                if (block != null)
                    block.append("  >> HUNLLEF ATTACK #").append(hunllefAttackCount)
                        .append(" (").append(style).append(")").append(System.lineSeparator());

                // Attack-STYLE change (verified: every 4 attacks, ~20t, starts
                // ranged) == the tick YOUR protection prayer must flip. This is the
                // boss's real attack cycle, independent of what it is protecting.
                if (style != null && !style.equals(prevHunllefAttackStyle))
                {
                    json("hunllef_attackstyle", "style", q(style), "prev", q(prevHunllefAttackStyle),
                        "attackNo", hunllefAttackCount);
                    if (block != null)
                        block.append("  >> HUNLLEF ATTACK-STYLE -> ").append(style)
                            .append(" (pray ").append(style).append(")").append(System.lineSeparator());
                    prevHunllefAttackStyle = style;
                }
            }

            json("proj",
                "id", p.getId(), "style", q(style),
                "src", q(srcName), "srcId", srcId,
                "srcTile", wp(p.getSourcePoint()),
                "target", q(t != null ? t.getName() : null),
                "rem", p.getRemainingCycles());
            if (block != null)
                block.append(String.format(" PROJ  id=%-5d %-9s rem=%-3d%s%s%n",
                    p.getId(), style != null ? style : "?", p.getRemainingCycles(),
                    srcName != null ? "  from " + srcName : "",
                    t != null ? "  ->" + t.getName() : ""));
        }
        // Drop finished projectiles so the identity set doesn't grow unbounded.
        seenProjectiles.retainAll(asSet(client.getProjectiles()));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private static Set<Projectile> asSet(Iterable<Projectile> list)
    {
        Set<Projectile> s = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Projectile p : list) s.add(p);
        return s;
    }

    private int regionId()
    {
        Player me = client.getLocalPlayer();
        if (me == null || me.getWorldLocation() == null) return -1;
        return me.getWorldLocation().getRegionID();
    }

    /**
     * The Hunllef's overhead protection icon, as a compact "archive:sprite" string
     * (or null if none). We don't hard-code the magic/ranged/melee mapping yet —
     * the raw ids are logged so they can be decoded from a capture by eye (read the
     * overhead the boss is visibly praying when the string changes). Once known,
     * promote the mapping into GauntletIds.
     */
    private static String overheadStr(NPC npc)
    {
        try
        {
            int[] arch = npc.getOverheadArchiveIds();
            short[] spr = npc.getOverheadSpriteIds();
            if (arch == null || spr == null) return null;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arch.length && i < spr.length; i++)
            {
                if (arch[i] < 0 && spr[i] < 0) continue;
                if (sb.length() > 0) sb.append('|');
                sb.append(arch[i]).append(':').append(spr[i]);
            }
            return sb.length() == 0 ? null : sb.toString();
        }
        catch (Exception ex) { return null; }
    }

    private String activePrayerString()
    {
        List<String> on = new ArrayList<>();
        for (Prayer p : Prayer.values())
        {
            try { if (client.isPrayerActive(p)) on.add(prettyPrayer(p)); }
            catch (Exception ignored) {}
        }
        return String.join(",", on);
    }

    private static String prettyPrayer(Prayer p)
    {
        return p.name().toLowerCase().replace('_', ' ').replace("protect from ", "prot ");
    }

    private WorldPoint iwp(Actor a)
    {
        LocalPoint lp = a.getLocalLocation();
        return lp == null ? null : WorldPoint.fromLocalInstance(client, lp);
    }

    private WorldPoint iwp(LocalPoint lp)
    {
        return lp == null ? null : WorldPoint.fromLocalInstance(client, lp);
    }

    private LocalPoint destLocal() { return client.getLocalDestinationLocation(); }

    private WorldPoint destTile()
    {
        LocalPoint lp = client.getLocalDestinationLocation();
        return lp == null ? null : WorldPoint.fromLocalInstance(client, lp);
    }

    private static String shortNpc(NPC npc)
    {
        String name = npc.getName() == null ? "npc" : npc.getName();
        return name + "#" + npc.getIndex();
    }

    private static String dir(int dx, int dy)
    {
        if (dx == 0 && dy == 0) return "";
        String ns = dy > 0 ? "N" : dy < 0 ? "S" : "";
        String ew = dx > 0 ? "E" : dx < 0 ? "W" : "";
        return ns + ew;
    }

    private static String sgn(int v) { return (v > 0 ? "+" : "") + v; }

    private int boosted(Skill s) { return client.getBoostedSkillLevel(s); }
    private int real(Skill s)    { return client.getRealSkillLevel(s); }

    private String itemName(int id)
    {
        try { return itemManager.getItemComposition(id).getName(); }
        catch (Exception ex) { return "item:" + id; }
    }

    // ------------------------------------------------------------------
    // Writers
    // ------------------------------------------------------------------
    private void json(String type, Object... kv)
    {
        if (json == null) return;
        StringBuilder sb = new StringBuilder(160);
        sb.append("{\"tick\":").append(client.getTickCount())
          .append(",\"ms\":").append(System.currentTimeMillis())
          .append(",\"type\":\"").append(type).append('"');
        for (int i = 0; i + 1 < kv.length; i += 2)
            sb.append(",\"").append(kv[i]).append("\":").append(kv[i + 1]);
        sb.append('}');
        try { json.write(sb.toString()); json.newLine(); } catch (IOException ignored) {}
    }

    private void txtEvent(String line)
    {
        if (text != null) pendingTxtEvents.add(line);
    }

    private void writeText(String s)
    {
        if (text == null) return;
        try { text.write(s); } catch (IOException ignored) {}
    }

    private void flush()
    {
        try { if (json != null) json.flush(); } catch (IOException ignored) {}
        try { if (text != null) text.flush(); } catch (IOException ignored) {}
    }

    private static void close(BufferedWriter w)
    {
        if (w == null) return;
        try { w.flush(); w.close(); } catch (IOException ignored) {}
    }

    private String wp(LocalPoint lp)
    {
        if (lp == null) return "null";
        return wp(WorldPoint.fromLocalInstance(client, lp));
    }

    private static String wp(WorldPoint wp)
    {
        if (wp == null) return "null";
        return "\"" + wp.getX() + "," + wp.getY() + "," + wp.getPlane() + "\"";
    }

    private static String tileStr(WorldPoint wp)
    {
        if (wp == null) return "(?,?)";
        return "(" + wp.getX() + "," + wp.getY() + ")";
    }

    private static String q(String s)
    {
        if (s == null) return "null";
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
