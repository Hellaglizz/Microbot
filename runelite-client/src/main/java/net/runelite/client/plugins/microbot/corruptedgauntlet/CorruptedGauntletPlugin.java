package net.runelite.client.plugins.microbot.corruptedgauntlet;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
    name = "Corrupted Gauntlet",
    description = "Automates a full Corrupted Gauntlet: prep (gather/craft) + Corrupted Hunllef, magic+ranged, auto-restart.",
    tags = {"microbot", "pvm", "gauntlet", "corrupted", "hunllef"},
    enabledByDefault = false
)
@Slf4j
public class CorruptedGauntletPlugin extends Plugin
{
    @Inject private CorruptedGauntletConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private CorruptedGauntletOverlay overlay;

    private final CorruptedGauntletScript script = new CorruptedGauntletScript();

    @Provides
    CorruptedGauntletConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(CorruptedGauntletConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        if (overlayManager != null) overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown() throws Exception
    {
        script.shutdown();
        if (overlayManager != null) overlayManager.remove(overlay);
    }
}
