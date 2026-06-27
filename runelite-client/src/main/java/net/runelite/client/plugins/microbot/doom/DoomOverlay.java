package net.runelite.client.plugins.microbot.doom;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

/**
 * Minimal status overlay — handy while filling in IDs so you can see what state
 * the tracker thinks the fight is in versus what's actually on screen.
 */
public class DoomOverlay extends OverlayPanel
{
    private DoomScript script;

    @Inject
    public DoomOverlay()
    {
        setPosition(OverlayPosition.TOP_LEFT);
    }

    public void setScript(DoomScript script) { this.script = script; }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Doom of Mokhaiotl")
            .color(Color.CYAN)
            .build());

        if (script != null)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("State")
                .right(String.valueOf(script.currentState()))
                .build());
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Delve")
                .right(String.valueOf(script.delveLevel()))
                .build());
        }
        return super.render(graphics);
    }
}
