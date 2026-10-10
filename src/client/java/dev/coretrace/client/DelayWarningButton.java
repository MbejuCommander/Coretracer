package dev.coretrace.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** A compact warning icon with a keyboard-accessible tooltip. */
final class DelayWarningButton extends Button {
    private final int color;
    DelayWarningButton(int x, int y, String warning) { this(x, y, warning, 0xFFFFD54F); }
    DelayWarningButton(int x, int y, String warning, int color) {
        super(x, y, 20, 20, Component.literal(warning), button -> {}, DEFAULT_NARRATION);
        this.color = color;
        setTooltip(Tooltip.create(Component.literal(warning)));
    }

    @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int center = getX() + 10, top = getY() + 2;
        for (int row = 0; row < 15; row++) {
            int half = row / 2;
            g.fill(center - half, top + row, center + half + 1, top + row + 1, color);
        }
        g.fill(center, top + 5, center + 1, top + 11, 0xFF583900);
        g.fill(center, top + 13, center + 1, top + 14, 0xFF583900);
    }
}
