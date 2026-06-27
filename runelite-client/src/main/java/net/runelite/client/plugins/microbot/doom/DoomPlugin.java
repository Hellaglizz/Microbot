package net.runelite.client.plugins.microbot.doom;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.regex.Pattern;

@PluginDescriptor(
    name = "Doom of Mokhaiotl",
    description = "Automates the Doom of Mokhaiotl delve boss (rotation-as-data state machine).",
    tags = {"microbot", "pvm", "doom", "mokhaiotl", "delve"},
    enabledByDefault = false
)
@Slf4j
public class DoomPlugin extends Plugin
{
    @Inject private DoomConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private DoomOverlay overlay;

    private final DoomScript script = new DoomScript();

    // "Delve level: N duration:" marks a just-completed delve, used to keep the
    // tracked level in sync regardless of where the script started.
    private static final Pattern DELVE_DONE = Pattern.compile("Delve level:?\\s*(\\d+)\\s*duration");
    private Pattern uniquePattern;

    @Provides
    DoomConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(DoomConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        try { uniquePattern = Pattern.compile(config.uniqueChatPattern()); }
        catch (Exception e) { uniquePattern = null; }

        if (overlayManager != null)
        {
            overlay.setScript(script);
            overlayManager.add(overlay);
        }
        script.run(config);
    }

    @Subscribe
    public void onChatMessage(ChatMessage e)
    {
        String msg = e.getMessage();
        if (msg == null) return;

        // Unique drop -> flag a bank trip at the next burrow hole.
        if (uniquePattern != null && uniquePattern.matcher(msg).find())
        {
            log.info("[Doom] unique drop detected: {}", msg);
            script.session().flagUnique();
        }

        // Keep delve level synced from the completion message.
        java.util.regex.Matcher m = DELVE_DONE.matcher(msg);
        if (m.find())
        {
            try { script.setDelveLevel(Integer.parseInt(m.group(1))); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void shutDown() throws Exception
    {
        script.shutdown();
        overlayManager.remove(overlay);
    }
}
