package dev.coretrace.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Retains vanilla keyboard and narration behavior. */
final class TraceButton extends Button {
    TraceButton(String label, int x, int y, int width, Runnable action) {
        super(x, y, width, 20, Component.literal(label), button -> action.run(), DEFAULT_NARRATION);
    }
    @Override protected void extractContents(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean highlight = active && isHoveredOrFocused();
        g.fill(x, y, x + w, y + h, !active ? 0xFF17232F : highlight ? 0xFF347568 : 0xFF304659);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, !active ? 0xFF111C27 : highlight ? 0xFF1D483F : 0xFF1B2E3E);
        if (highlight) g.fill(x, y + 3, x + 2, y + h - 3, 0xFF60E3BB);
        var font = Minecraft.getInstance().font;
        g.centeredText(font, font.plainSubstrByWidth(getMessage().getString(), Math.max(1, w - 12)),
                x + w / 2, y + (h - 8) / 2, active ? 0xFFE3EFF5 : 0xFF637889);
    }
}
