package dev.coretrace.client;

import dev.coretrace.core.TaskCommand;
import dev.coretrace.core.TaskDefinition;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Paginated editor; the list has no fixed command-count limit. */
final class TaskCommandsScreen extends BaseScreen {
    private final TaskDefinition task;
    private final Map<TaskCommand, String> delays = new IdentityHashMap<>();
    private int page;
    private String error = "";
    TaskCommandsScreen(Screen parent, TaskDefinition task) {
        super(parent, "queue.commands"); this.task = task; task.migrateCommands();
    }
    @Override protected void init() {
        super.init();
        int perPage = Math.max(1, (height - 145) / 27);
        int pages = Math.max(1, (task.commands.size() + perPage - 1) / perPage);
        page = Math.clamp(page, 0, pages - 1);
        button(tr("queue.add"), left, 40, panelWidth, () -> {
            task.commands.add(new TaskCommand()); page = (task.commands.size() - 1) / perPage; rebuildWidgets();
        });
        for (int i = page * perPage; i < Math.min(task.commands.size(), (page + 1) * perPage); i++) {
            int index = i, y = 80 + (i % perPage) * 27;
            TaskCommand item = task.commands.get(i);
            var command = field(tr("queue.custom") + " " + (i + 1), item.command, left, y, panelWidth - 158, 256);
            command.setHint(Component.literal("/command"));
            command.setResponder(value -> item.command = value);
            var delay = field(tr("queue.custom_delay"), delays.getOrDefault(item, "" + item.delayMs), left + panelWidth - 154, y, 66, 7);
            delay.setHint(Component.literal("750"));
            delay.setResponder(value -> {
                delays.put(item, value);
                try { item.delayMs = TaskDefinition.delay(value); } catch (IllegalArgumentException ignored) { }
            });
            button("↑", left + panelWidth - 84, y, 24, () -> {
                java.util.Collections.swap(task.commands, index, index - 1); page = (index - 1) / perPage; rebuildWidgets();
            }).active = i > 0;
            button("↓", left + panelWidth - 56, y, 24, () -> {
                java.util.Collections.swap(task.commands, index, index + 1); page = (index + 1) / perPage; rebuildWidgets();
            }).active = i + 1 < task.commands.size();
            button("×", left + panelWidth - 28, y, 28, () -> {
                task.commands.remove(index); delays.remove(item); error = ""; rebuildWidgets();
            }).setTooltip(Tooltip.create(Component.literal(tr("queue.delete"))));
        }
        int half = (panelWidth - 6) / 2;
        button(tr("common.previous"), left, height - 67, half, () -> { page--; rebuildWidgets(); }).active = page > 0;
        button(tr("common.next"), left + half + 6, height - 67, panelWidth - half - 6, () -> { page++; rebuildWidgets(); }).active = page + 1 < pages;
        button(tr("ui.save_back"), left, height - 28, panelWidth, this::onClose);
    }
    @Override public void onClose() {
        for (int i = 0; i < task.commands.size(); i++) {
            TaskCommand item = task.commands.get(i);
            try {
                item.delayMs = TaskDefinition.delay(delays.getOrDefault(item, "" + item.delayMs));
                item.validate();
            } catch (IllegalArgumentException e) {
                error = tr("queue.command_invalid", i + 1);
                page = i / Math.max(1, (height - 145) / 27); rebuildWidgets(); return;
            }
        }
        mod.saveConfig(); super.onClose();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        label(g, tr("queue.custom"), left, 68, panelWidth - 158, 0xFFB8C9D9);
        label(g, "Delay (ms)", left + panelWidth - 154, 68, 80, 0xFFB8C9D9);
        label(g, error.isEmpty() ? tr("queue.commands_hint") : error, left, height - 42, panelWidth,
                error.isEmpty() ? 0xFFB8C9D9 : 0xFFFF9494);
    }
}
