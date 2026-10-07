package dev.coretrace.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class TaskProfilesScreen extends BaseScreen {
    private EditBox name;
    private int page;
    private String error = "";

    TaskProfilesScreen(Screen parent) { super(parent, "queue.profiles"); }

    @Override protected void init() {
        String draft = name == null ? "" : name.getValue();
        super.init();
        name = field(tr("profiles.name"), draft, left, 40, panelWidth - 100, 64);
        name.setHint(Component.literal(tr("profiles.name")));
        int perPage = Math.max(1, (height - 152) / 27);
        var profiles = mod.config().taskProfiles;
        int pages = Math.max(1, (profiles.size() + perPage - 1) / perPage);
        page = Math.clamp(page, 0, pages - 1);
        button(tr("profiles.save"), left + panelWidth - 96, 40, 96, () -> {
            try {
                mod.config().saveTaskProfile(name.getValue());
                mod.saveConfig(); name.setValue(""); error = "";
                page = (profiles.size() - 1) / perPage; rebuildWidgets();
            } catch (IllegalArgumentException e) { error = tr("profiles.invalid_name"); }
        }).active = !mod.busy();
        for (int i = page * perPage; i < Math.min(profiles.size(), (page + 1) * perPage); i++) {
            var profile = profiles.get(i);
            int y = 83 + (i % perPage) * 27;
            var load = button(tr("profiles.entry", profile.name, profile.tasks.size()), left, y, panelWidth - 34, () -> {
                mod.config().loadTaskProfile(profile); mod.saveConfig();
                mod.noticeKey("profiles.loaded", profile.name); onClose();
            });
            load.active = !mod.busy();
            load.setTooltip(Tooltip.create(Component.literal(tr("profiles.load_hint", profile.name))));
            var remove = button("×", left + panelWidth - 28, y, 28, () -> {
                profiles.remove(profile); mod.saveConfig(); rebuildWidgets();
            });
            remove.active = !mod.busy();
            remove.setTooltip(Tooltip.create(Component.literal(tr("profiles.delete"))));
        }
        int half = (panelWidth - 6) / 2;
        button(tr("common.previous"), left, height - 62, half, () -> { page--; rebuildWidgets(); }).active = page > 0;
        button(tr("common.next"), left + half + 6, height - 62, panelWidth - half - 6, () -> { page++; rebuildWidgets(); }).active = page + 1 < pages;
        button(tr("common.back"), left, height - 28, panelWidth, this::onClose);
    }
    private boolean wasBusy;
    @Override public void tick() {
        if (wasBusy != mod.busy()) { wasBusy = mod.busy(); rebuildWidgets(); }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        label(g, error.isEmpty() ? tr("profiles.hint") : error, left, 67, panelWidth,
                error.isEmpty() ? 0xFFB8C9D9 : 0xFFFF9494);
        if (mod.config().taskProfiles.isEmpty()) label(g, tr("profiles.empty"), left, 87, panelWidth, 0xFFB8C9D9);
    }
}
