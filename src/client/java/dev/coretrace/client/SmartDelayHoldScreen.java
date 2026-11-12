package dev.coretrace.client;

import dev.coretrace.core.Config;
import dev.coretrace.core.SmartDelay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Configures active confirmed-page time required before speeding up after each spam kick. */
final class SmartDelayHoldScreen extends BaseScreen {
    private final Config target;
    private final Runnable persist;
    private final EditBox[] periods = new EditBox[4];
    private final String[] drafts = new String[4];
    private String error = "";

    SmartDelayHoldScreen(Screen parent, Config target, Runnable persist) {
        super(parent, "delay.hold_title");
        this.target = target; this.persist = persist;
        int[] values = SmartDelay.validHoldSeconds(target.smartDelayHoldSeconds)
                ? target.smartDelayHoldSeconds : SmartDelay.defaultHoldSeconds();
        for (int i = 0; i < 4; i++) drafts[i] = Integer.toString(values[i]);
    }

    @Override protected void init() {
        for (int i = 0; i < 4; i++) if (periods[i] != null) drafts[i] = periods[i].getValue();
        super.init();
        int half = (panelWidth - 6) / 2;
        for (int i = 0; i < 4; i++) {
            int x = left + (i % 2) * (half + 6);
            int y = 70 + (i / 2) * 60;
            periods[i] = field(tr("delay.hold_stage", i + 1), drafts[i], x, y, half, 3);
            periods[i].setHint(Component.literal(Integer.toString(SmartDelay.defaultHoldSeconds()[i])));
            periods[i].setTooltip(Tooltip.create(Component.literal(tr("delay.hold_hint"))));
        }
        button(tr("delay.hold_defaults"), left, 170, panelWidth, () -> {
            int[] defaults = SmartDelay.defaultHoldSeconds();
            for (int i = 0; i < 4; i++) periods[i].setValue(Integer.toString(defaults[i]));
            error = "";
        });
        button(tr("ui.save_back"), left, height - 28, panelWidth, () -> {
            String[] values = new String[4];
            for (int i = 0; i < 4; i++) values[i] = periods[i].getValue();
            try { target.smartDelayHoldSeconds = SmartDelay.parseHoldSeconds(values); }
            catch (IllegalArgumentException e) { error = tr("delay.hold_invalid"); return; }
            persist.run(); onClose();
        });
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        for (int i = 0; i < 4; i++)
            label(g, tr("delay.hold_stage", i + 1), periods[i].getX(), 55 + (i / 2) * 60,
                    periods[i].getWidth(), 0xFFB8C9D9);
        label(g, error.isEmpty() ? tr("delay.hold_hint") : error, left, 151, panelWidth,
                error.isEmpty() ? 0xFF8DDCC4 : 0xFFFF5555);
    }
}
