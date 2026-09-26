package dev.coretrace.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import java.util.List;

final class HistoryScreen extends BaseScreen {
    private boolean favorites;
    private int offset;
    HistoryScreen(Screen parent) { super(parent, "ui.title.history"); }
    @Override protected void init() {
        super.init();
        button(tr(favorites ? "ui.show_recent" : "ui.show_favorites"), left, 39, panelWidth,
                () -> { favorites = !favorites; offset = 0; rebuildWidgets(); });
        List<String> items = favorites ? mod.config().favorites : mod.config().recentQueries;
        int perPage = Math.max(1, (height - 132) / 25);
        offset = Math.min(offset, Math.max(0, items.size() - 1));
        for (int i = offset; i < Math.min(items.size(), offset + perPage); i++) {
            String value = items.get(i);
            button(font.plainSubstrByWidth("/" + value, panelWidth - 16), left, 69 + (i - offset) * 25, panelWidth,
                    () -> minecraft.gui.setScreen(new MainScreen(((MainScreen) parent).parent, value)));
        }
        int half = (panelWidth - 6) / 2;
        var back = button(tr("common.previous"), left, height - 54, half, () -> { offset = Math.max(0, offset - perPage); rebuildWidgets(); });
        var next = button(tr("common.next"), left + half + 6, height - 54, panelWidth - half - 6, () -> { offset += perPage; rebuildWidgets(); });
        back.active = offset > 0; next.active = offset + perPage < items.size();
        button(tr("common.back"), left, height - 28, panelWidth, this::onClose);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        if ((favorites ? mod.config().favorites : mod.config().recentQueries).isEmpty())
            label(g, tr(favorites ? "ui.favorites_empty" : "ui.history_empty"), left, 77, panelWidth, 0xFFB8C9D9);
    }
}
