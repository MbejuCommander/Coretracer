package dev.coretrace.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class SettingsScreen extends BaseScreen {
    private final String[] values;
    private final EditBox[] fields = new EditBox[6];
    private int tab;
    private String error = "";
    SettingsScreen(Screen parent) {
        super(parent, "ui.title.settings");
        var c = mod.config();
        values = new String[]{"" + c.delayMs, "" + c.timeoutMs, "" + c.settleMs, "" + c.maxPages, "" + c.csvPagesPerFile, c.csvFileName};
    }
    private void rememberFields() {
        for (int i = 0; i < fields.length; i++) if (fields[i] != null) values[i] = fields[i].getValue();
    }
    @Override protected void init() {
        rememberFields(); java.util.Arrays.fill(fields, null);
        super.init(); int half = (panelWidth - 6) / 2, third = (panelWidth - 12) / 3;
        String[] tabs = {"ui.settings.tab_capture", "ui.settings.tab_csv", "ui.settings.tab_audio"};
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            button((tab == i ? "§a" : "") + tr(tabs[i]), left + i * (third + 6), 39,
                    i == 2 ? panelWidth - 2 * (third + 6) : third, () -> { mod.stopPreview(); tab = index; rebuildWidgets(); });
        }
        var c = mod.config();
        if (tab == 0) {
            button(tr("ui.settings.auto", yes(c.automatic)), left, 69, half, () -> { c.automatic = !c.automatic; mod.saveConfig(); rebuildWidgets(); });
            button(tr("ui.settings.hide", yes(c.hideCapturedChat)), left + half + 6, 69, panelWidth - half - 6,
                    () -> { c.hideCapturedChat = !c.hideCapturedChat; mod.saveConfig(); rebuildWidgets(); });
            button(tr("ui.language", mod.language().nativeName()), left, 95, half, () -> { mod.changeLanguage(); rebuildWidgets(); });
            button(tr("ui.settings.hovers", yes(c.captureHovers)), left + half + 6, 95, panelWidth - half - 6,
                    () -> { c.captureHovers = !c.captureHovers; mod.saveConfig(); rebuildWidgets(); });
            fields[0] = field(tr("ui.settings.delay_field"), values[0], left, 132, half, 6);
            fields[1] = field(tr("ui.settings.timeout_field"), values[1], left + half + 6, 132, panelWidth - half - 6, 6);
            fields[2] = field(tr("ui.settings.settle_field"), values[2], left, 171, half, 6);
            fields[3] = field(tr("ui.settings.limit_field"), values[3], left + half + 6, 171, panelWidth - half - 6, 4);
        } else if (tab == 1) {
            fields[5] = field(tr("ui.settings.csv_name"), values[5], left, 84, panelWidth, 104);
            fields[5].setHint(Component.literal(tr("ui.settings.csv_name_hint")));
            fields[4] = field(tr("ui.settings.csv_split_field"), values[4], left, 126, panelWidth, 4);
            button(tr("ui.settings.csv_columns"), left, 160, panelWidth, () -> minecraft.gui.setScreen(new CsvColumnsScreen(this)));
        } else {
            soundButton(false, 92); soundButton(true, 142);
            button(tr("ui.sounds.stop"), left, 174, panelWidth, mod::stopPreview);
        }
        button(tr("ui.save_back"), left, height - 28, half, () -> { if (save()) onClose(); });
        button(tr("common.back"), left + half + 6, height - 28, panelWidth - half - 6, this::onClose);
    }
    private void soundButton(boolean finish, int y) {
        String id = finish ? mod.config().finishSound : mod.config().startSound;
        int previewWidth = Math.min(86, panelWidth / 3);
        var select = button((id.isBlank() ? tr("ui.sound.off") : id) + " ▾", left, y, panelWidth - previewWidth - 6,
                () -> minecraft.gui.setScreen(new SoundPickerScreen(this, finish)));
        select.setTooltip(Tooltip.create(Component.literal(id.isBlank() ? tr("ui.sound.off") : id)));
        button(tr("ui.settings.sound_preview"), left + panelWidth - previewWidth, y, previewWidth, () -> mod.previewSound(id)).active = !id.isBlank();
    }
    private boolean save() {
        rememberFields();
        String csvName;
        try { csvName = dev.coretrace.core.CsvFileNames.normalize(values[5]); }
        catch (IllegalArgumentException e) {
            error = "ui.settings.csv_name_invalid"; tab = 1; rebuildWidgets(); return false;
        }
        try {
            int d = Integer.parseInt(values[0]), t = Integer.parseInt(values[1]);
            int s = Integer.parseInt(values[2]), m = Integer.parseInt(values[3]), cp = Integer.parseInt(values[4]);
            if (d < 750 || d > 30000 || t < 5000 || t > 180000 || s < 1500 || s > t - 1000 || m < 1 || m > 5000 || cp < 0 || cp > 5000)
                throw new IllegalArgumentException();
            var c = mod.config();
            c.delayMs = d; c.timeoutMs = t; c.settleMs = s; c.maxPages = m; c.csvPagesPerFile = cp;
            c.csvFileName = csvName;
            mod.saveConfig(); return true;
        } catch (IllegalArgumentException e) { error = "ui.settings.invalid"; return false; }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta); int half = (panelWidth - 6) / 2;
        if (tab == 0) {
            label(g, tr("ui.settings.delay"), left, 121, half, 0xFFB8C9D9);
            label(g, tr("ui.settings.timeout"), left + half + 6, 121, half, 0xFFB8C9D9);
            label(g, tr("ui.settings.settle"), left, 160, half, 0xFFB8C9D9);
            label(g, tr("ui.settings.limit"), left + half + 6, 160, half, 0xFFB8C9D9);
        } else if (tab == 1) {
            label(g, tr("ui.settings.csv_name"), left, 72, panelWidth, 0xFFB8C9D9);
            label(g, tr("ui.settings.csv_split_field"), left, 114, panelWidth, 0xFFB8C9D9);
            try {
                int part = Integer.parseInt(fields[4].getValue()) > 0 ? 1 : 0;
                String filename = dev.coretrace.core.CsvFileNames.fileName(fields[5].getValue(), mod.language(), part);
                label(g, tr("ui.settings.csv_name_preview", filename), left, 185, panelWidth, 0xFF8DA9BA);
            } catch (IllegalArgumentException ignored) { /* Keep editing incomplete values. */ }
        } else {
            label(g, tr("ui.settings.start_sound", ""), left, 78, panelWidth, 0xFFB8C9D9);
            label(g, tr("ui.settings.finish_sound", ""), left, 128, panelWidth, 0xFFB8C9D9);
        }
        if (height >= 238) label(g, tr(error.isEmpty() ? "ui.settings.next_capture" : error), left, height - 42,
                panelWidth, error.isEmpty() ? 0xFF8298AE : 0xFFFF9494);
    }
    @Override public void removed() { mod.stopPreview(); super.removed(); }
}
