package net.runelite.client.plugins.microbot.corruptedgauntlet;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class CorruptedGauntletOverlay extends OverlayPanel
{
    @Inject
    CorruptedGauntletOverlay(CorruptedGauntletPlugin plugin)
    {
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        try
        {
            panelComponent.getChildren().add(TitleComponent.builder()
                .text("Corrupted Gauntlet v" + CorruptedGauntletScript.version)
                .build());
            panelComponent.getChildren().add(LineComponent.builder()
                .left("State:")
                .right(String.valueOf(CorruptedGauntletScript.state))
                .build());
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(Microbot.status == null ? "" : Microbot.status)
                .build());
        }
        catch (Exception ignored) {}
        return super.render(graphics);
    }
}
