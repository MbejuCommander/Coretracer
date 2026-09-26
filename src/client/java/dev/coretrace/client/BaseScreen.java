package dev.coretrace.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

abstract class BaseScreen extends Screen {
    protected final Screen parent;
    protected final CoreTraceClient mod = CoreTraceClient.INSTANCE;
    protected int left, panelWidth;
    private final String titleKey;
    BaseScreen(Screen parent, String titleKey) {
        super(Component.literal(CoreTraceClient.INSTANCE.tr(titleKey)));
        this.parent = parent; this.titleKey = titleKey;
    }
    protected String tr(String key, Object... args) { return mod.tr(key, args); }
    protected String yes(boolean value) { return tr(value ? "common.yes" : "common.no"); }
    @Override public Component getTitle() { return Component.literal(tr(titleKey)); }
    @Override public Component getNarrationMessage() { return getTitle(); }
    @Override protected void init() { panelWidth = Math.min(620, width - 24); left = (width - panelWidth) / 2; }
    protected Button button(String label, int x, int y, int w, Runnable action) {
        return addRenderableWidget(new TraceButton(label, x, y, w, action));
    }
    protected EditBox field(String label, String value, int x, int y, int w, int max) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.literal(label));
        box.setMaxLength(max); box.setValue(value); addRenderableWidget(box); return box;
    }
    protected void label(GuiGraphicsExtractor g, String text, int x, int y, int maxWidth, int color) {
        g.text(font, font.plainSubstrByWidth(text, maxWidth), x, y, color);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0xF5091019);
        g.fill(left - 10, 5, left + panelWidth + 10, height - 5, 0xFF263D4D);
        g.fill(left - 9, 6, left + panelWidth + 9, height - 6, 0xFF101E2B);
        g.fill(left - 8, 6, left + panelWidth + 8, 30, 0xFF193340);
        g.fill(left, 30, left + panelWidth, 31, 0xFF4BCFA6);
        g.centeredText(font, getTitle(), width / 2, 13, 0xFFF0FAFF);
        g.fill(left, height - 33, left + panelWidth, height - 32, 0xFF263D4D);
        extractPanels(g);
        super.extractRenderState(g, mx, my, delta);
    }
    protected void extractPanels(GuiGraphicsExtractor g) {}
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
