package dev.coretrace.client;
import dev.coretrace.core.TaskDefinition;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
final class TaskQueueScreen extends BaseScreen {
    private int page;
    private EditBox delay;
    private EditBox excelName;
    private DelayWarningButton delayWarning;
    private String error = "";
    private boolean wasBusy;
    private boolean wasPaused, wasSkippable;
    TaskQueueScreen(Screen parent) { super(parent, "queue.title"); }
    private boolean saveDelay() {
        int parsedDelay;
        try { parsedDelay = TaskDefinition.taskDelay(delay.getValue()); }
        catch (IllegalArgumentException e) { error = tr("queue.invalid_delay"); return false; }
        String parsedName;
        try { parsedName = excelName == null ? mod.config().queueExcelName
                : dev.coretrace.core.QueueExcel.normalizeName(excelName.getValue()); }
        catch (IllegalArgumentException e) { error = tr("queue.invalid_excel_name"); return false; }
        mod.config().taskDelayMs = parsedDelay;
        mod.config().queueExcelName = parsedName;
        mod.saveConfig(); error = ""; return true;
    }
    @Override protected void init() {
        String draft = delay == null ? "" + mod.config().taskDelayMs : delay.getValue();
        String nameDraft = excelName == null ? mod.config().queueExcelName : excelName.getValue();
        super.init(); excelName = null; int third = (panelWidth - 12) / 3;
        button(tr("queue.add"), left, 40, third, () -> {
            if (!saveDelay()) return;
            TaskDefinition task = new TaskDefinition(); mod.config().tasks.add(task); mod.saveConfig();
            minecraft.gui.setScreen(new TaskEditScreen(this, task));
        }).active = !mod.busy();
        button(tr("queue.sounds"), left + third + 6, 40, third, () -> {
            if (saveDelay()) minecraft.gui.setScreen(new QueueSoundsScreen(this));
        });
        delay = field(tr("queue.delay"), draft, left + (third + 6) * 2, 40, panelWidth - (third + 6) * 2 - 24, 7);
        delay.setHint(net.minecraft.network.chat.Component.literal("3000 ms"));
        delayWarning = addRenderableWidget(new DelayWarningButton(left + panelWidth - 20, 40,
                tr("queue.short_delay_warning")));
        delayWarning.visible = shortDelay(draft);
        delay.setResponder(value -> delayWarning.visible = shortDelay(value));
        button(tr("queue.profiles"), left, 66, third, () -> {
            if (saveDelay()) minecraft.gui.setScreen(new TaskProfilesScreen(this));
        }).active = !mod.busy();
        button(tr("queue.clear"), left + third + 6, 66, third, () -> {
            mod.config().tasks.clear(); mod.saveConfig(); page = 0; rebuildWidgets();
        }).active = !mod.busy() && !mod.config().tasks.isEmpty();
        var excel = button(tr("queue.excel", yes(mod.config().queueExcel)), left, 92, panelWidth, () -> {
            if (!saveDelay()) return;
            mod.config().queueExcel = !mod.config().queueExcel; mod.saveConfig(); rebuildWidgets();
        });
        excel.active = !mod.busy();
        excel.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                net.minecraft.network.chat.Component.literal(tr("queue.excel.tooltip"))));
        if (mod.config().queueExcel) {
            excelName = field(tr("queue.excel_name"), nameDraft, left + third + 6, 120,
                    panelWidth - third - 6, 105);
            excelName.setHint(net.minecraft.network.chat.Component.literal("tasks.xlsx"));
            excelName.active = !mod.busy();
        }
        int taskTop = mod.config().queueExcel ? 146 : 120;
        int perPage = Math.max(1, (height - (taskTop + 112)) / 27);
        var tasks = mod.config().tasks;
        int pages = Math.max(1, (tasks.size() + perPage - 1) / perPage);
        page = Math.clamp(page, 0, pages - 1);
        for (int i = page * perPage; i < Math.min(tasks.size(), (page + 1) * perPage); i++) {
            int index = i, y = taskTop + (i % perPage) * 27;
            TaskDefinition task = tasks.get(i);
            String name = task.csvName == null || task.csvName.isBlank() ? task.command : task.csvName;
            button((i + 1) + ". " + name, left, y, panelWidth - 158, () -> {
                if (saveDelay()) minecraft.gui.setScreen(new TaskEditScreen(this, task));
            }).active = !mod.busy();
            button(tr("queue.copy"), left + panelWidth - 154, y, 54, () -> {
                if (!saveDelay()) return;
                TaskDefinition duplicate = mod.config().duplicateTask(index);
                mod.saveConfig(); page = (index + 1) / perPage;
                minecraft.gui.setScreen(new TaskEditScreen(this, duplicate));
            }).active = !mod.busy();
            button("↑", left + panelWidth - 96, y, 24, () -> { java.util.Collections.swap(tasks, index, index - 1); mod.saveConfig(); rebuildWidgets(); }).active = i > 0 && !mod.busy();
            button("↓", left + panelWidth - 69, y, 24, () -> { java.util.Collections.swap(tasks, index, index + 1); mod.saveConfig(); rebuildWidgets(); }).active = i + 1 < tasks.size() && !mod.busy();
            var remove = button("×", left + panelWidth - 42, y, 42, () -> { tasks.remove(index); mod.saveConfig(); rebuildWidgets(); });
            remove.setTooltip(net.minecraft.client.gui.components.Tooltip.create(net.minecraft.network.chat.Component.literal(tr("queue.delete"))));
            remove.active = !mod.busy();
        }
        button("←", left, height - 67, 30, () -> { page--; rebuildWidgets(); }).active = page > 0;
        button("→", left + 34, height - 67, 30, () -> { page++; rebuildWidgets(); }).active = page + 1 < pages;
        int controlWidth = (panelWidth - 6) / 2;
        var pause = button(tr(mod.queuePaused() ? "queue.resume" : "queue.pause"), left, height - 105,
                controlWidth, () -> { mod.toggleQueuePause(); rebuildWidgets(); });
        pause.active = mod.queueActive();
        pause.setTooltip(net.minecraft.client.gui.components.Tooltip.create(net.minecraft.network.chat.Component.literal(
                tr(mod.queuePaused() ? "queue.resume.tooltip" : "queue.pause.tooltip"))));
        var cancelTask = button(tr("queue.cancel_task"), left + controlWidth + 6, height - 105,
                panelWidth - controlWidth - 6, () -> { mod.cancelCurrentTask(); rebuildWidgets(); });
        cancelTask.active = mod.canSkipCurrentTask();
        cancelTask.setTooltip(net.minecraft.client.gui.components.Tooltip.create(net.minecraft.network.chat.Component.literal(
                tr("queue.cancel_task.tooltip"))));
        button(tr("queue.run"), left + 70, height - 67, (panelWidth - 76) / 2, () -> { if (saveDelay()) { mod.startTasks(); rebuildWidgets(); } }).active = !mod.busy() && !tasks.isEmpty();
        var stop = button(tr("queue.stop"), left + 76 + (panelWidth - 76) / 2, height - 67,
                (panelWidth - 76) / 2, () -> { mod.cancel(); rebuildWidgets(); });
        stop.active = mod.queueActive();
        stop.setTooltip(net.minecraft.client.gui.components.Tooltip.create(net.minecraft.network.chat.Component.literal(
                tr("queue.stop.tooltip"))));
        button(tr("common.back"), left, height - 28, panelWidth, this::onClose);
        wasBusy = mod.busy(); wasPaused = mod.queuePaused(); wasSkippable = mod.canSkipCurrentTask();
    }
    @Override public void tick() {
        if (wasBusy != mod.busy() || wasPaused != mod.queuePaused() || wasSkippable != mod.canSkipCurrentTask())
            rebuildWidgets();
    }
    @Override public void onClose() { if (saveDelay()) super.onClose(); }
    private static boolean shortDelay(String value) {
        try { return TaskDefinition.taskDelay(value) < 3000; }
        catch (IllegalArgumentException e) { return false; }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        label(g, tr("queue.delay"), left + (panelWidth - 12) / 3 * 2 + 12, 66, panelWidth / 3, 0xFFB8C9D9);
        if (mod.config().queueExcel)
            label(g, tr("queue.excel_name"), left, 125, (panelWidth - 12) / 3, 0xFFB8C9D9);
        label(g, error.isEmpty() ? mod.status() : error, left + 70, height - 43, panelWidth - 70, 0xFF93E8D9);
    }
}
