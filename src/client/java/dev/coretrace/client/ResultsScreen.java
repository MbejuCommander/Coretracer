package dev.coretrace.client;

import dev.coretrace.core.MessageData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ResultsScreen extends BaseScreen {
    private EditBox search;
    private List<FormattedCharSequence> wrapped = List.of();
    private List<String> matches = List.of();
    private int offset;
    private long refreshAt;
    ResultsScreen(Screen parent) { super(parent, "ui.title.results"); }
    @Override protected void init() {
        String text = search == null ? "" : search.getValue();
        super.init();
        search = field(tr("ui.filter"), text, left, 41, panelWidth, 128);
        search.setHint(Component.literal(tr("ui.filter_hint")));
        search.setResponder(s -> { offset = 0; refresh(); });
        int third = (panelWidth - 12) / 3;
        button(tr("common.previous"), left, height - 54, third, () -> offset = Math.max(0, offset - visibleLines()));
        button(tr("common.next"), left + third + 6, height - 54, third, () -> offset = Math.min(Math.max(0, wrapped.size() - visibleLines()), offset + visibleLines()));
        button(tr("ui.copy_filtered"), left + (third + 6) * 2, height - 54, panelWidth - (third + 6) * 2,
                () -> { minecraft.keyboardHandler.setClipboard(String.join("\n", matches)); mod.noticeKey("notice.copied"); });
        button(tr("common.back"), left, height - 28, panelWidth, this::onClose);
        refresh();
    }
    private int visibleLines() { return Math.max(1, (height - 148) / 11); }
    private void refresh() {
        if (search == null) return;
        String term = search.getValue().toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        if (mod.engine() != null) for (MessageData m : mod.engine().visibleMessages()) {
            if (m.text().toLowerCase(Locale.ROOT).contains(term)) result.add(m.text());
        }
        matches = List.copyOf(result);
        List<FormattedCharSequence> lines = new ArrayList<>();
        // Bound rendering work; exported files always retain the full capture.
        for (String s : result.stream().limit(5000).toList()) lines.addAll(font.split(Component.literal(s), panelWidth - 12));
        wrapped = List.copyOf(lines); offset = Math.min(offset, Math.max(0, wrapped.size() - visibleLines()));
        refreshAt = System.currentTimeMillis() + 1000;
    }
    @Override public void tick() { if (System.currentTimeMillis() >= refreshAt) refresh(); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        label(g, tr("ui.results_count", matches.size()), left, 69, panelWidth, 0xFF8298AE);
        g.fill(left, 83, left + panelWidth, height - 63, 0xFF0B1420);
        g.enableScissor(left, 83, left + panelWidth, height - 63);
        for (int i = offset; i < Math.min(wrapped.size(), offset + visibleLines()); i++)
            g.text(font, wrapped.get(i), left + 5, 88 + (i - offset) * 11, 0xFFD9E6F3);
        g.disableScissor();
    }
}
