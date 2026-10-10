package dev.coretrace.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

final class ReconnectSettingsScreen extends BaseScreen {
    private EditBox delay, attempts;
    private String delayValue, attemptsValue;
    private boolean invalid;
    ReconnectSettingsScreen(Screen parent) {
        super(parent, "reconnect.title");
        delayValue = "" + mod.config().reconnectDelayMs;
        attemptsValue = "" + mod.config().reconnectAttempts;
    }
    @Override protected void init() {
        if (delay != null) { delayValue = delay.getValue(); attemptsValue = attempts.getValue(); }
        super.init(); var c = mod.config();
        button(tr("reconnect.enabled", yes(c.autoReconnect)), left, 52, panelWidth,
                () -> { c.autoReconnect = !c.autoReconnect; mod.saveConfig(); rebuildWidgets(); });
        delay = field(tr("reconnect.delay"), delayValue, left, 94, panelWidth, 19);
        attempts = field(tr("reconnect.attempts"), attemptsValue, left, 134, panelWidth, 10);
        button(tr("reconnect.show_hint", yes(c.showReconnectHint)), left, 164, panelWidth,
                () -> { c.showReconnectHint = !c.showReconnectHint; mod.saveConfig(); rebuildWidgets(); });
        button(tr("ui.save_back"), left, height - 28, panelWidth, () -> {
            try {
                long d = delay.getValue().isBlank() ? 5000 : Long.parseLong(delay.getValue().trim());
                int n = attempts.getValue().isBlank() ? 3 : Integer.parseInt(attempts.getValue().trim());
                if (d < 0 || n < 1) throw new IllegalArgumentException();
                c.reconnectDelayMs = d; c.reconnectAttempts = n; mod.saveConfig(); onClose();
            } catch (IllegalArgumentException e) { invalid = true; }
        });
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        label(g, tr("reconnect.delay"), left, 82, panelWidth, 0xFFB8C9D9);
        label(g, tr("reconnect.attempts"), left, 122, panelWidth, 0xFFB8C9D9);
        if (invalid) label(g, tr("reconnect.invalid"), left, height - 42, panelWidth, 0xFFFF9494);
    }
}
