package dev.coretrace.client;
import dev.coretrace.core.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
final class TaskEditScreen extends BaseScreen {
    private final TaskDefinition task;
    private EditBox command, csv;
    private String error = "";
    TaskEditScreen(Screen parent, TaskDefinition task) { super(parent, "queue.task"); this.task = task; }
    private void remember() {
        if (command == null) return;
        task.command = command.getValue(); task.csvName = csv.getValue();
    }
    private boolean save(boolean validate) {
        remember();
        try {
            if (validate) { task.validate(); task.csvName = CsvFileNames.normalize(task.csvName); }
            mod.saveConfig(); error = ""; return true;
        } catch (IllegalArgumentException e) { error = tr("queue.invalid"); return false; }
    }
    @Override protected void init() {
        remember(); super.init(); int half = (panelWidth - 6) / 2;
        command = field(tr("queue.command"), task.command, left, 45, panelWidth, 256);
        csv = field(tr("ui.settings.csv_name"), task.csvName, left, 79, half, 104);
        button(tr("queue.csv_settings"), left + half + 6, 79, panelWidth - half - 6, () -> {
            if (!save(false)) return;
            Config draft = (task.csvSettings == null ? mod.config() : task.csvSettings).captureCopy();
            draft.csvFileName = task.csvName;
            minecraft.gui.setScreen(new SettingsScreen(this, draft, 1, () -> { task.csvSettings = draft; mod.saveConfig(); }));
        });
        button(tr("queue.universal", yes(task.universalSettings)), left, 104, half, () -> {
            remember(); task.universalSettings = !task.universalSettings; mod.saveConfig(); rebuildWidgets();
        });
        button(tr("queue.task_settings"), left + half + 6, 104, panelWidth - half - 6, () -> {
            if (!save(false)) return;
            if (task.taskSettings == null) { task.taskSettings = mod.config().captureCopy(); task.taskSettings.startSound = ""; task.taskSettings.finishSound = ""; }
            minecraft.gui.setScreen(new SettingsScreen(this, task.taskSettings, 2, mod::saveConfig));
        }).active = !task.universalSettings;
        task.migrateCommands();
        button(tr("queue.commands_count", task.commands.size()), left, 141, panelWidth, () -> {
            if (save(false)) minecraft.gui.setScreen(new TaskCommandsScreen(this, task));
        });
        button(tr("queue.csv_inherit"), left, 177, panelWidth, () -> { task.csvSettings = null; mod.saveConfig(); rebuildWidgets(); }).active = task.csvSettings != null;
        button(tr("ui.save_back"), left, height - 28, half, () -> { if (save(true)) super.onClose(); });
        button(tr("common.back"), left + half + 6, height - 28, panelWidth - half - 6, this::onClose);
    }
    @Override public void onClose() { if (save(false)) super.onClose(); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        label(g, tr("queue.command"), left, 34, panelWidth, 0xFFB8C9D9);
        label(g, tr("ui.settings.csv_name"), left, 68, panelWidth, 0xFFB8C9D9);
        label(g, tr("queue.custom"), left, 130, panelWidth, 0xFFB8C9D9);
        if (!error.isEmpty()) label(g, error, left, height - 42, panelWidth, 0xFFFF9494);
    }
}
