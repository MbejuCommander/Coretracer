package dev.coretrace.client;

import dev.coretrace.core.Config;
import dev.coretrace.core.SmartDelay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class SmartDelayScreen extends BaseScreen {
    private final Config target;
    private final Runnable persist;
    private EditBox phrase, minimum, window;
    private String minimumDraft, windowDraft, error = "";
    private final EditBox[] estimates = new EditBox[4];
    private String draft;
    SmartDelayScreen(Screen parent, Config target, Runnable persist) {
        super(parent, "delay.title"); this.target = target; this.persist = persist;
        draft = target.smartDelayKickMessage; minimumDraft = "" + target.smartDelayMinimumMs; windowDraft = "" + target.smartDelayWindowSeconds;
    }
    @Override protected void init() {
        if (phrase != null) { draft = phrase.getValue(); minimumDraft = minimum.getValue(); windowDraft = window.getValue(); }
        super.init();
        phrase = field(tr("delay.phrase"), draft, left, 51, panelWidth, 256);
        int half = (panelWidth - 6) / 2;
        minimum = field(tr("delay.minimum"), minimumDraft, left, 106, half, 5);
        window = field(tr("delay.window"), windowDraft, left + half + 6, 106, panelWidth - half - 6, 3);
        window.setHint(Component.literal("5"));
        window.setTooltip(Tooltip.create(Component.literal(tr("delay.window_hint"))));
        minimum.setHint(Component.literal("240"));
        minimum.setTooltip(Tooltip.create(Component.literal(tr("delay.minimum_hint"))));
        phrase.setHint(Component.literal(SmartDelay.DEFAULT_REASON));
        phrase.setTooltip(Tooltip.create(Component.literal(tr("delay.phrase_hint"))));
        button(tr("delay.per_server", yes(target.smartDelayPerServer)), left, 74, panelWidth, () -> {
            target.smartDelayPerServer = !target.smartDelayPerServer; persist.run(); rebuildWidgets();
        }).setTooltip(Tooltip.create(Component.literal(tr("delay.per_server_hint"))));
        int third = (panelWidth - 18) / 4;
        for (int i = 0; i < 4; i++) {
            estimates[i] = field(tr("delay.value." + i), "", left + i * (third + 6), 145, i == 3 ? panelWidth - 3 * (third + 6) : third, 40);
            estimates[i].setEditable(false);
            estimates[i].setTooltip(Tooltip.create(Component.literal(tr("delay.estimates_hint"))));
        }
        button(tr("delay.hold_button"), left, 190, half, () -> minecraft.gui.setScreen(new SmartDelayHoldScreen(this, target, persist)))
                .setTooltip(Tooltip.create(Component.literal(tr("delay.hold_hint"))));
        button(tr("delay.reset"), left + half + 6, 190, panelWidth - half - 6, () -> mod.resetDelayLearning(target));
        for (var child : children()) if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) widget.setHeight(18);
        button(tr("ui.save_back"), left, height - 28, panelWidth, () -> {
            int minValue, windowValue;
            try { minValue = SmartDelay.parseMinimum(minimum.getValue()); }
            catch (IllegalArgumentException e) { error = tr("delay.minimum_invalid"); return; }
            try { windowValue = SmartDelay.parseWindow(window.getValue()); }
            catch (IllegalArgumentException e) { error = tr("delay.window_invalid"); return; }
            target.smartDelayMinimumMs = minValue; target.smartDelayWindowSeconds = windowValue;
            target.smartDelayKickMessage = phrase.getValue().isBlank() ? SmartDelay.DEFAULT_REASON : phrase.getValue().strip();
            persist.run(); onClose();
        });
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        var learner = mod.delayLearning(target);
        var state = learner.state();
        var recent = learner.recent();
        String[] numbers = {recent.count() == 0 ? "—" : recent.minimum() + " ms",
                recent.count() == 0 ? "—" : recent.average() + " ms",
                recent.count() == 0 ? "—" : recent.maximum() + " ms", learner.delay() + " ms"};
        for (int i = 0; i < 4; i++) if (!estimates[i].getValue().equals(numbers[i])) estimates[i].setValue(numbers[i]);
        super.extractRenderState(g, mx, my, delta);
        label(g, tr("delay.phrase"), left, 39, panelWidth, 0xFFB8C9D9);
        label(g, tr("delay.minimum"), minimum.getX(), 96, minimum.getWidth(), 0xFFB8C9D9);
        label(g, tr("delay.window"), window.getX(), 96, window.getWidth(), 0xFFB8C9D9);
        int third = (panelWidth - 18) / 4;
        for (int i = 0; i < 4; i++) label(g, tr("delay.value." + i), left + i * (third + 6), 134, third, 0xFFB8C9D9);
        label(g, tr("delay.last_three"), left, 125, 3 * (third + 6) - 6, 0xFFB8C9D9);
        label(g, error.isEmpty() ? (state.stableHoldRemaining > 0 ? tr("delay.holding", (state.stableHoldRemaining + 999) / 1000) : tr(recent.count() == 0 ? "delay.learning" : "delay.learned", target.smartDelayWindowSeconds)) : error, left, 165, panelWidth, error.isEmpty() ? 0xFF8DDCC4 : 0xFFFF5555);
        String last = state.lastKickAt == 0 ? tr("delay.never") : java.time.Instant.ofEpochMilli(state.lastKickAt)
                .atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        label(g, tr("delay.last_kick", last), left, 177, panelWidth, 0xFFB8C9D9);
    }
}
