package dev.coretrace.client;

import dev.coretrace.core.LookupCommand;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MainScreen extends BaseScreen {
    private String draft;
    private EditBox query;
    private Button start, pause, cancel, favorite;
    public MainScreen(Screen parent, String draft) { super(parent, "ui.title.main"); this.draft = draft; }
    @Override protected void init() {
        super.init();
        if (query != null) draft = query.getValue();
        query = field(tr("ui.query"), draft == null ? mod.lastQuery() : draft, left, 49, panelWidth, 256);
        query.setResponder(value -> draft = value);
        int third = (panelWidth - 12) / 3;
        start = button(tr("ui.start"), left, 76, third, () -> mod.start(query.getValue()));
        favorite = button(tr("ui.favorite.off"), left + third + 6, 76, third, () -> {
            var c = LookupCommand.fromInput(query.getValue());
            if (c.isEmpty()) { mod.noticeKey("notice.favorite_invalid"); return; }
            var favorites = mod.config().favorites;
            if (!favorites.remove(c.get().command())) favorites.addFirst(c.get().command());
            mod.saveConfig();
        });
        button(tr("ui.history"), left + (third + 6) * 2, 76, panelWidth - (third + 6) * 2, () -> minecraft.gui.setScreen(new HistoryScreen(this)));
        pause = button(tr("ui.pause"), left, 131, third, mod::togglePause);
        cancel = button(tr("ui.cancel_save"), left + third + 6, 131, third, mod::cancel);
        button(tr("ui.results"), left + (third + 6) * 2, 131, panelWidth - (third + 6) * 2, () -> minecraft.gui.setScreen(new ResultsScreen(this)));
        button(tr("ui.settings"), left, 157, third, () -> minecraft.gui.setScreen(new SettingsScreen(this)));
        button(tr("ui.last_export"), left + third + 6, 157, third, mod::openLast);
        button(tr("ui.export_folder"), left + (third + 6) * 2, 157, panelWidth - (third + 6) * 2, mod::openExports);
        button(tr("common.back"), left, height - 28, panelWidth, this::onClose);
        setInitialFocus(query);
    }
    @Override public void tick() {
        boolean active = mod.active(); start.active = !active && minecraft.getConnection() != null;
        pause.active = cancel.active = active;
        pause.setMessage(Component.literal(tr(active && mod.engine().paused() ? "ui.resume" : "ui.pause")));
        boolean saved = LookupCommand.fromInput(query.getValue()).map(c -> mod.config().favorites.contains(c.command())).orElse(false);
        favorite.setMessage(Component.literal(tr(saved ? "ui.favorite.on" : "ui.favorite.off")));
    }
    @Override protected void extractPanels(GuiGraphicsExtractor g) {
        g.fill(left - 3, 98, left + panelWidth + 3, 125, 0xFF182F3A);
        g.fill(left - 3, 98, left - 1, 125, mod.active() ? 0xFF4BCFA6 : 0xFF506A7F);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        label(g, tr("ui.query_hint"), left, 37, panelWidth, 0xFFB8C9D9);
        label(g, mod.status(), left, 102, panelWidth, 0xFF93E8D9);
        g.fill(left, 118, left + panelWidth, 122, 0xFF25384B);
        var engine = mod.engine();
        if (engine != null && engine.totalPages() > 0) {
            int fill = (int) ((long) panelWidth * engine.completedPages() / engine.totalPages());
            g.fill(left, 118, left + fill, 122, 0xFF2DD4BF);
        }
        if (height >= 234) label(g, tr("ui.shortcuts", yes(mod.config().automatic)), left, 186, panelWidth, 0xFFB8C9D9);
        if (height >= 255) label(g, tr("ui.server_requirement"), left, 200, panelWidth, 0xFF8298AE);
    }
}
