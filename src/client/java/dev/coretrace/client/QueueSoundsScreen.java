package dev.coretrace.client;
import net.minecraft.client.gui.screens.Screen;
final class QueueSoundsScreen extends BaseScreen {
    QueueSoundsScreen(Screen parent) { super(parent, "queue.sounds"); }
    @Override protected void init() {
        super.init(); var c = mod.config();
        button(tr("ui.settings.start_sound", c.queueStartSound.isBlank() ? tr("ui.sound.off") : c.queueStartSound), left, 60, panelWidth,
            () -> minecraft.gui.setScreen(new SoundPickerScreen(this, false, () -> c.queueStartSound, id -> { c.queueStartSound = id; mod.saveConfig(); })));
        button(tr("ui.settings.finish_sound", c.queueFinishSound.isBlank() ? tr("ui.sound.off") : c.queueFinishSound), left, 100, panelWidth,
            () -> minecraft.gui.setScreen(new SoundPickerScreen(this, true, () -> c.queueFinishSound, id -> { c.queueFinishSound = id; mod.saveConfig(); })));
        button(tr("common.back"), left, height - 28, panelWidth, this::onClose);
    }
}
