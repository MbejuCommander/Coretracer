package dev.coretrace.client;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Bounded widgets, even with thousands of vanilla events loaded. */
final class SoundPickerScreen extends BaseScreen {
    private final boolean finishSound;
    private List<Choice> sounds = List.of(), matches = List.of();
    private EditBox search;
    private Button[] choices, previews;
    private Button previous, next;
    private int offset, perPage;

    SoundPickerScreen(Screen parent, boolean finishSound) {
        super(parent, "ui.title.sound_picker"); this.finishSound = finishSound;
    }
    private String selected() { return finishSound ? mod.config().finishSound : mod.config().startSound; }
    private void select(String id) {
        if (finishSound) mod.config().finishSound = id; else mod.config().startSound = id;
        mod.saveConfig(); onClose();
    }
    @Override protected void init() {
        String term = search == null ? "" : search.getValue();
        super.init();
        var manager = minecraft.getSoundManager();
        sounds = manager.getAvailableSounds().stream().filter(id -> id.getNamespace().equals("minecraft"))
                .sorted(java.util.Comparator.comparing(Object::toString)).map(id -> {
                    var event = manager.getSoundEvent(id);
                    String subtitle = event == null || event.getSubtitle() == null ? "" : event.getSubtitle().getString();
                    return new Choice(id.toString(), subtitle);
                }).toList();
        search = field(tr("ui.sounds.search"), term, left, 41, panelWidth, 160);
        search.setHint(Component.literal(tr("ui.sounds.search_hint")));
        int half = (panelWidth - 6) / 2;
        button(tr("ui.sound.off"), left, 66, half, () -> select(""));
        button(tr("ui.sounds.stop"), left + half + 6, 66, panelWidth - half - 6, mod::stopPreview);
        perPage = Math.max(1, (height - 165) / 24);
        choices = new Button[perPage]; previews = new Button[perPage];
        int previewWidth = Math.min(86, panelWidth / 3);
        for (int i = 0; i < perPage; i++) {
            final int slot = i;
            choices[i] = button("", left, 102 + i * 24, panelWidth - previewWidth - 6,
                    () -> select(matches.get(offset + slot).id()));
            previews[i] = button(tr("ui.settings.sound_preview"), left + panelWidth - previewWidth, 102 + i * 24,
                    previewWidth, () -> mod.previewSound(matches.get(offset + slot).id()));
        }
        previous = button(tr("common.previous"), left, height - 54, half, () -> { offset -= perPage; updateRows(); });
        next = button(tr("common.next"), left + half + 6, height - 54, panelWidth - half - 6,
                () -> { offset += perPage; updateRows(); });
        button(tr("common.back"), left, height - 28, panelWidth, this::onClose);
        search.setResponder(value -> { offset = 0; filter(value); });
        filter(term); setInitialFocus(search);
    }
    private void filter(String term) {
        String[] words = term.toLowerCase(Locale.ROOT).strip().split("\\s+");
        matches = sounds.stream().filter(choice -> {
            String haystack = (choice.id() + " " + choice.subtitle() + " " + choice.id().replace('.', ' ').replace('_', ' '))
                    .toLowerCase(Locale.ROOT);
            return java.util.Arrays.stream(words).allMatch(haystack::contains);
        }).toList();
        updateRows();
    }
    private void updateRows() {
        offset = Math.clamp(offset, 0, Math.max(0, ((matches.size() - 1) / perPage) * perPage));
        for (int i = 0; i < perPage; i++) {
            boolean visible = offset + i < matches.size();
            choices[i].visible = previews[i].visible = visible;
            choices[i].active = previews[i].active = visible;
            if (visible) {
                Choice choice = matches.get(offset + i);
                choices[i].setMessage(Component.literal((choice.id().equals(selected()) ? "§a✓ " : "") + choice.id().substring(10)));
                choices[i].setTooltip(Tooltip.create(Component.literal(choice.id() + (choice.subtitle().isBlank() ? "" : "\n" + choice.subtitle()))));
            }
        }
        previous.active = offset > 0; next.active = offset + perPage < matches.size();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        label(g, tr(finishSound ? "ui.sounds.finish_count" : "ui.sounds.start_count", matches.size(), sounds.size(),
                matches.isEmpty() ? 0 : offset / perPage + 1, Math.max(1, (matches.size() + perPage - 1) / perPage)), left, 91, panelWidth, 0xFF8DA9BA);
        if (matches.isEmpty()) label(g, tr("ui.sounds.empty"), left + 8, 111, panelWidth - 16, 0xFFD1DEEA);
    }
    @Override public void removed() { mod.stopPreview(); super.removed(); }
    private record Choice(String id, String subtitle) {}
}
