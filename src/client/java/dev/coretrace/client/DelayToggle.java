package dev.coretrace.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Small switch with vanilla keyboard activation and a narrated state. */
final class DelayToggle extends Button {
    private final boolean enabled;
    DelayToggle(int x, int y, int height, boolean enabled, String label, Runnable action) {
        super(x, y, 36, height, Component.literal(label), b -> action.run(), DEFAULT_NARRATION);
        this.enabled = enabled;
    }
    @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int x = getX(), y = getY(), h = getHeight();
        g.fill(x, y, x + 36, y + h, isHoveredOrFocused() ? 0xFFE3EFF5 : 0xFF8298AE);
        g.fill(x + 1, y + 1, x + 35, y + h - 1, enabled ? 0xFF267358 : 0xFF37434D);
        int knob = enabled ? x + 20 : x + 2;
        g.fill(knob, y + 2, knob + 14, y + h - 2, 0xFFE3EFF5);
        if (enabled) g.fill(x + 9, y + 5, x + 11, y + h - 5, 0xFFFFFFFF);
        else {
            g.fill(x + 24, y + 5, x + 30, y + h - 5, 0xFFE3EFF5);
            g.fill(x + 25, y + 6, x + 29, y + h - 6, 0xFF37434D);
        }
    }
}
