package dev.coretrace.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class SettingsScreen extends BaseScreen {
    private final String[] values;
    private final EditBox[] fields = new EditBox[7];
    private int tab;
    private final dev.coretrace.core.Config target;
    private final Runnable persist;
    private final int mode; // 0 universal, 1 CSV only, 2 task capture/audio
    private String error = "";
    SettingsScreen(Screen parent) {
        this(parent, CoreTraceClient.INSTANCE.config(), 0, CoreTraceClient.INSTANCE::saveConfig);
    }
    SettingsScreen(Screen parent, dev.coretrace.core.Config target, int mode, Runnable persist) {
        super(parent, "ui.title.settings");
        this.target = target; this.mode = mode; this.persist = persist; this.tab = mode == 1 ? 1 : 0;
        var c = target;
        values = new String[]{"" + c.delayMs, "" + c.timeoutMs, "" + c.settleMs, "" + c.maxPages, "" + c.csvPagesPerFile, c.csvFileName, "" + c.autoResumeDelayMs};
    }
    private void rememberFields() {
        for (int i = 0; i < fields.length; i++) if (fields[i] != null) values[i] = fields[i].getValue();
    }
    @Override protected void init() {
        rememberFields(); java.util.Arrays.fill(fields, null);
        super.init(); int half = (panelWidth - 6) / 2, third = (panelWidth - 12) / 3;
        String[] tabs = mode == 0
                ? new String[]{"ui.settings.tab_capture", "ui.settings.tab_csv", "ui.settings.tab_audio", "recovery.settings"}
                : new String[]{"ui.settings.tab_capture", "ui.settings.tab_csv", "ui.settings.tab_audio"};
        int tabWidth = (panelWidth - (tabs.length - 1) * 6) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            if ((mode == 1 && i != 1) || (mode == 2 && i == 1)) continue;
            final int index = i;
            button((tab == i ? "§a" : "") + tr(tabs[i]), left + i * (tabWidth + 6), 39,
                    i == tabs.length - 1 ? panelWidth - i * (tabWidth + 6) : tabWidth, () -> { mod.stopPreview(); tab = index; rebuildWidgets(); });
        }
        var c = target;
        if (tab == 0) {
            button(tr("ui.settings.auto", yes(c.automatic)), left, 63, half, () -> { c.automatic = !c.automatic; persist.run(); rebuildWidgets(); });
            button(tr("ui.settings.hide", yes(c.hideCapturedChat)), left + half + 6, 63, panelWidth - half - 6,
                    () -> { c.hideCapturedChat = !c.hideCapturedChat; persist.run(); rebuildWidgets(); });
            button(tr("ui.language", target.selectedLanguage().nativeName()), left, 87, half, () -> { target.language = target.selectedLanguage().next().code(); persist.run(); rebuildWidgets(); });
            button(tr("ui.settings.hovers", yes(c.captureHovers)), left + half + 6, 87, panelWidth - half - 6,
                    () -> { c.captureHovers = !c.captureHovers; persist.run(); rebuildWidgets(); });
            fields[0] = field(tr("ui.settings.delay_field"), values[0], left, 121, half, 6);
            fields[1] = field(tr("ui.settings.timeout_field"), values[1], left + half + 6, 121, panelWidth - half - 6, 6);
            fields[2] = field(tr("ui.settings.settle_field"), values[2], left, 154, half, 6);
            fields[3] = field(tr("ui.settings.limit_field"), values[3], left + half + 6, 154, panelWidth - half - 6, 4);
            button(tr("ui.settings.single_page", yes(c.acceptSinglePage)), left, 177, panelWidth,
                    () -> { c.acceptSinglePage = !c.acceptSinglePage; persist.run(); rebuildWidgets(); })
                    .setTooltip(Tooltip.create(Component.literal(tr("ui.settings.single_page_hint"))));
        } else if (tab == 1) {
            fields[5] = field(tr("ui.settings.csv_name"), values[5], left, 76, panelWidth, 104);
            fields[5].setEditable(mode != 1);
            fields[5].setHint(Component.literal(tr("ui.settings.csv_name_hint")));
            fields[4] = field(tr("ui.settings.csv_split_field"), values[4], left, 112, panelWidth, 4);
            button(tr("ui.settings.csv_columns"), left, 136, half, () -> minecraft.gui.setScreen(new CsvColumnsScreen(this, target, persist)));
            button(tr("ui.settings.reset_csv", yes(c.resetCsvNameAfterExport)), left + half + 6, 136, panelWidth - half - 6,
                    () -> { c.resetCsvNameAfterExport = !c.resetCsvNameAfterExport; persist.run(); rebuildWidgets(); })
                    .setTooltip(Tooltip.create(Component.literal(tr("ui.settings.reset_csv_hint"))));
            button(tr("ui.settings.excel_columns", yes(c.csvExcelAutoColumns)), left, 160, half,
                    () -> { c.csvExcelAutoColumns = !c.csvExcelAutoColumns; persist.run(); rebuildWidgets(); })
                    .setTooltip(Tooltip.create(Component.literal(tr("ui.settings.excel_columns_hint"))));
            var smart = button(tr("smart.enabled", yes(c.smartCsvEnabled())), left + half + 6, 160, panelWidth - half - 6,
                    () -> { c.csvSmart = !c.smartCsvEnabled(); persist.run(); rebuildWidgets(); })
                    ;
            smart.setTooltip(Tooltip.create(Component.literal(tr(c.csvColumns.contains("server_timestamp") ? "smart.hint" : "smart.timestamp_warning"))));
            smart.active = c.csvColumns.contains("server_timestamp");
        } else if (tab == 3) {
            button(tr("recovery.enabled", yes(c.autoResume)), left, 72, panelWidth,
                    () -> { c.autoResume = !c.autoResume; persist.run(); rebuildWidgets(); });
            fields[6] = field(tr("recovery.delay"), values[6], left, 113, panelWidth, 19);
            fields[6].setHint(Component.literal("4000"));
            button(tr("reconnect.title"), left, 158, half, () -> minecraft.gui.setScreen(new ReconnectSettingsScreen(this)));
            button(tr("recovery.discard"), left + half + 6, 158, panelWidth - half - 6, () -> { mod.discardRecovery(); rebuildWidgets(); })
                    .active = mod.hasRecovery() && !mod.active() && !mod.queueActive();
        } else {
            soundButton(false, 92); soundButton(true, 142);
            button(tr("ui.sounds.stop"), left, 174, panelWidth, mod::stopPreview);
        }
        button(tr("ui.save_back"), left, height - 28, half, () -> { if (save()) onClose(); });
        button(tr("common.back"), left + half + 6, height - 28, panelWidth - half - 6, this::onClose);
    }
    private void soundButton(boolean finish, int y) {
        String id = finish ? target.finishSound : target.startSound;
        int previewWidth = Math.min(86, panelWidth / 3);
        var select = button((id.isBlank() ? tr("ui.sound.off") : id) + " ▾", left, y, panelWidth - previewWidth - 6,
                () -> minecraft.gui.setScreen(new SoundPickerScreen(this, finish, () -> finish ? target.finishSound : target.startSound, value -> { if (finish) target.finishSound = value; else target.startSound = value; persist.run(); })));
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
            long resumeDelay;
            try { resumeDelay = dev.coretrace.core.ResumeCountdown.parseDelay(values[6]); }
            catch (IllegalArgumentException e) { error = "recovery.invalid_delay"; tab = 3; rebuildWidgets(); return false; }
            int d = Integer.parseInt(values[0]), t = Integer.parseInt(values[1]);
            int s = Integer.parseInt(values[2]), m = Integer.parseInt(values[3]), cp = Integer.parseInt(values[4]);
            if (d < 750 || d > 30000 || t < 5000 || t > 180000 || s < 1500 || s > t - 1000 || m < 1 || m > 5000 || cp < 0 || cp > 5000)
                throw new IllegalArgumentException();
            var c = target;
            c.delayMs = d; c.timeoutMs = t; c.settleMs = s; c.maxPages = m; c.csvPagesPerFile = cp;
            c.csvFileName = csvName; c.autoResumeDelayMs = resumeDelay;
            persist.run(); return true;
        } catch (IllegalArgumentException e) { error = "ui.settings.invalid"; return false; }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta); int half = (panelWidth - 6) / 2;
        if (tab == 0) {
            label(g, tr("ui.settings.delay"), left, 110, half, 0xFFB8C9D9);
            label(g, tr("ui.settings.timeout"), left + half + 6, 110, half, 0xFFB8C9D9);
            label(g, tr("ui.settings.settle"), left, 143, half, 0xFFB8C9D9);
            label(g, tr("ui.settings.limit"), left + half + 6, 143, half, 0xFFB8C9D9);
        } else if (tab == 1) {
            label(g, tr("ui.settings.csv_name"), left, 64, panelWidth, 0xFFB8C9D9);
            label(g, tr("ui.settings.csv_split_field"), left, 100, panelWidth, 0xFFB8C9D9);
            try {
                int part = Integer.parseInt(fields[4].getValue()) > 0 ? 1 : 0;
                String filename = dev.coretrace.core.CsvFileNames.fileName(fields[5].getValue(), mod.language(), part);
                label(g, tr("ui.settings.csv_name_preview", filename), left, 185, panelWidth, 0xFF8DA9BA);
            } catch (IllegalArgumentException ignored) { /* Keep editing incomplete values. */ }
        } else if (tab == 3) {
            label(g, tr("recovery.delay"), left, 101, panelWidth, 0xFFB8C9D9);
            label(g, tr("recovery.hint"), left, 140, panelWidth, 0xFFB8C9D9);
            label(g, tr("recovery.same_server"), left, 184, panelWidth, 0xFF8DA9BA);
        } else {
            label(g, tr("ui.settings.start_sound", ""), left, 78, panelWidth, 0xFFB8C9D9);
            label(g, tr("ui.settings.finish_sound", ""), left, 128, panelWidth, 0xFFB8C9D9);
        }
        if (height >= 238) label(g, tr(error.isEmpty() ? "ui.settings.next_capture" : error), left, height - 42,
                panelWidth, error.isEmpty() ? 0xFF8298AE : 0xFFFF9494);
    }
    @Override public void removed() { mod.stopPreview(); super.removed(); }
}
