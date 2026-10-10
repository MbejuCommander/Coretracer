package dev.coretrace.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.coretrace.core.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public final class CoreTraceClient implements ClientModInitializer {
    public static CoreTraceClient INSTANCE;
    private static final Logger LOGGER = LoggerFactory.getLogger("CoreTrace");
    private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CoreTrace-files"); t.setDaemon(true); return t;
    });
    private Config config;
    private Path configPath, exports;
    private CaptureEngine engine;
    private TaskQueue taskQueue;
    private Config captureConfig;
    private boolean saving;
    private Context context;
    private boolean ownCommand, openPending, commandCancelled;
    private String feedbackKey = "status.ready";
    private Object[] feedbackArgs = new Object[0];
    private Path lastDirectory;
    private Path lastExportFile;
    private net.minecraft.client.resources.sounds.SoundInstance preview;
    private KeyMapping key;
    private long readyAfter;
    private RecoveryStore recoveryStore;
    private RecoveryStore.State recovery;
    private RecoveryStore.Capture resumeStarting;
    private java.util.List<CaptureEngine.Page> recoveredPages = java.util.List.of();
    private ResumeCountdown resumeCountdown;
    private boolean recoveryWaiting, restoring;
    private int recoveryGeneration;
    private net.minecraft.client.multiplayer.ServerData reconnectServer;
    private ReconnectPlan reconnectPlan;
    private net.minecraft.client.gui.screens.Screen reconnectFailureScreen;
    private static final class Context {
        final SessionFiles files;
        volatile boolean failed;
        Context(Path path, Language language, Config config) { files = new SessionFiles(path, language, config); }
    }
    @FunctionalInterface private interface IoAction { void run() throws IOException; }

    @Override public void onInitializeClient() {
        INSTANCE = this;
        configPath = FabricLoader.getInstance().getConfigDir().resolve("coretrace.json");
        exports = FabricLoader.getInstance().getGameDir().resolve("coretrace/exports").toAbsolutePath();
        try { config = Config.load(configPath); }
        catch (IOException e) { config = new Config(); setFeedback("notice.config_load"); LOGGER.warn(tr("notice.config_load"), e); }
        recoveryStore = new RecoveryStore(exports.getParent().resolve("recovery.json"), exports);
        try { recovery = recoveryStore.load(); recoveryWaiting = recovery != null; }
        catch (IOException e) { LOGGER.warn("Cannot load CoreTrace recovery", e); setFeedback("recovery.load_failed"); }
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("coretrace", "resume_countdown"), (graphics, delta) -> {
                    var mc = Minecraft.getInstance();
                    if (resumeCountdown != null && mc.player != null) {
                        graphics.centeredText(mc.font, tr("recovery.countdown", resumeCountdown.seconds(now())),
                                mc.getWindow().getGuiScaledWidth() / 2, mc.getWindow().getGuiScaledHeight() / 2 + 18, 0xFFFF5555);
                    }
                });
        if (!config.lastExportFile.isBlank()) {
            try {
                lastExportFile = Path.of(config.lastExportFile).toAbsolutePath();
                lastDirectory = lastExportFile.getParent();
            } catch (RuntimeException e) { LOGGER.debug("Invalid saved export path", e); }
        }
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.coretrace.menu", InputConstants.KEY_F8,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("coretrace", "main"))));
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            if (!ownCommand && busy() && LookupCommand.isCoreProtect(command)) {
                noticeKey("notice.busy_command");
                return false;
            }
            if (!ownCommand && config.automatic && now() < readyAfter && LookupCommand.parse(command).filter(LookupCommand::query).isPresent()) {
                noticeKey("notice.cooldown", (readyAfter - now()) / 1000 + 1);
                return false;
            }
            return true;
        });
        ClientSendMessageEvents.COMMAND.register(command -> {
            if (!ownCommand && !busy() && config.automatic) LookupCommand.parse(command).filter(LookupCommand::query)
                    .ifPresent(c -> begin(c, false));
        });
        ClientSendMessageEvents.COMMAND_CANCELED.register(command -> {
            if (ownCommand) commandCancelled = true;
            if (ownCommand && active()) engine.endKey(CaptureEngine.Outcome.CANCELLED, "reason.other_mod", now());
        });
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay) return true;
            if (!active()) {
                if (now() < readyAfter) {
                    var data = ComponentReader.read(message, false, now());
                    if (CoreProtectParser.header(data) || CoreProtectParser.entry(data) || CoreProtectParser.pagination(data).isPresent())
                        readyAfter = Math.max(readyAfter, now() + config.settleMs);
                }
                return true;
            }
            boolean captured = engine.accept(ComponentReader.read(message, (captureConfig.captureHovers || captureConfig.smartCsvEnabled()), now()), now());
            return !captured || !captureConfig.hideCapturedChat;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) openPending = true;
            if (openPending) { openPending = false; client.gui.setScreen(new MainScreen(client.gui.screen(), null)); }
            if (active()) engine.tick(now());
            if (queueActive()) taskQueue.tick(now());
            reconnectTick();
            recoveryTick();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            reconnectServer = client.getCurrentServer();
            reconnectPlan = null; reconnectFailureScreen = null;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            disconnected("reason.disconnected");
            if (config.autoReconnect && reconnectServer != null && !reconnectServer.isRealm()) {
                reconnectPlan = new ReconnectPlan(config.reconnectDelayMs, config.reconnectAttempts);
                reconnectFailureScreen = null;
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            disconnected("reason.shutdown");
            io.shutdown();
            try { if (!io.awaitTermination(4, TimeUnit.SECONDS)) LOGGER.warn(tr("notice.unfinished_writes")); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            literal("coretrace").requires(FabricClientCommandSource::attended)
                .executes(c -> { openPending = true; return 1; })
                .then(literal("start").then(argument("query", StringArgumentType.greedyString())
                    .executes(c -> start(StringArgumentType.getString(c, "query")) ? 1 : 0)))
                .then(literal("pause").executes(c -> { if (active() && !engine.paused()) togglePause(); return 1; }))
                .then(literal("resume").executes(c -> { if (active() && engine.paused()) togglePause(); return 1; }))
                .then(literal("cancel").executes(c -> { cancel(); return 1; }))
                .then(literal("folder").executes(c -> { openExports(); return 1; }))
                .then(literal("status").executes(c -> { displayNotice(status()); return 1; }))
        ));
    }
    public boolean start(String input) {
        var parsed = LookupCommand.fromInput(input);
        if (parsed.isEmpty()) { noticeKey("notice.invalid_query"); return false; }
        if (busy()) { noticeKey("notice.busy"); return false; }
        return begin(parsed.get(), true);
    }
    private boolean begin(LookupCommand command, boolean send) {
        return begin(command, send, config.captureCopy(), null);
    }
    private boolean begin(LookupCommand command, boolean send, Config runConfig, TaskDefinition task) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) { noticeKey("notice.connect"); return false; }
        if (active() || saving) { noticeKey("notice.busy"); return false; }
        if (now() < readyAfter) { noticeKey("notice.cooldown", (readyAfter - now()) / 1000 + 1); return false; }
        long started = now();
        if (config.showReconnectHint) Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(
                Component.literal("[CoreTrace] " + tr("reconnect.hint")).withStyle(net.minecraft.ChatFormatting.GREEN));
        String server = serverKey();
        RecoveryStore.Capture savedResume = resumeStarting; resumeStarting = null;
        RecoveryStore.Capture resumed = savedResume != null && runConfig.smartCsvEnabled() ? savedResume.at(1, 0) : savedResume;
        if (resumed != null && runConfig.smartCsvEnabled()) noticeKey("smart.refresh");
        Path path;
        try { path = resumed != null ? recoveryStore.directory(resumed) : CsvFileNames.reserveDirectory(exports, runConfig.csvFileName,
                NAME.format(Instant.ofEpochMilli(started)) + "-" + UUID.randomUUID().toString().substring(0, 8)); }
        catch (IOException e) { noticeKey("notice.save_failed"); return false; }
        captureConfig = runConfig;
        var settings = runConfig.settings();
        Context ctx = new Context(path, settings.language(), runConfig); context = ctx;
        TaskQueue owner = task == null ? null : taskQueue;
        recoveryWaiting = false; resumeCountdown = null;
        if (command.query()) {
            var capture = resumed != null ? resumed : new RecoveryStore.Capture(path.getFileName().toString(),
                    command.command(), started, 1, 0, runConfig.captureCopy());
            recovery = new RecoveryStore.State(server, capture, owner == null ? null : owner.snapshot(now()),
                    recovery == null ? "" : recovery.finishSound());
        } else recovery = null;
        RecoveryStore.State initialRecovery = recovery;
        queue(ctx, () -> {
            if (resumed != null) ctx.files.resume(resumed.page());
            else ctx.files.begin(command.command(), server, started);
            recoveryStore.save(initialRecovery);
        });
        CaptureEngine.Sink sink = new CaptureEngine.Sink() {
            @Override public void send(String text) {
                checkpointCapture(engine.expectedPage(), engine.totalPages(), null);
                sendLookup(text);
            }
            @Override public void line(int page, MessageData m) { queue(ctx, () -> ctx.files.line(page, m)); }
            @Override public void page(CaptureEngine.Page p) {
                queue(ctx, () -> ctx.files.page(p));
                checkpointCapture(p.number(), engine.totalPages(), p);
            }
            @Override public void finish(CaptureEngine.Snapshot snapshot) {
                if (!snapshot.outcome().successful()
                        && snapshot.outcome() != CaptureEngine.Outcome.DISCONNECTED)
                    readyAfter = now() + runConfig.timeoutMs;
                saving = true;
                setFeedback("status.saving");
                queue(ctx, () -> {
                    ctx.files.finish(snapshot);
                    mc.execute(() -> {
                        saving = false;
                        if (snapshot.outcome().successful() && runConfig.resetCsvNameAfterExport) {
                            if (task == null) config.csvFileName = CsvFileNames.afterExport(config.csvFileName, runConfig.csvFileName, true);
                            if (task != null && config.tasks.contains(task)) task.csvName = CsvFileNames.afterExport(task.csvName, runConfig.csvFileName, true);
                        }
                        lastDirectory = ctx.files.directory();
                        lastExportFile = ctx.files.lastCsvPath();
                        if (lastExportFile != null) {
                            config.lastExportFile = lastExportFile.toString();
                            saveConfig();
                        }
                        playConfiguredSound(runConfig.finishSound);
                        if (context == ctx) setFeedback("notice.finished", snapshot.outcome(), snapshot.pages().size());
                        displayNotice(tr("notice.finished", snapshot.outcome(), snapshot.pages().size()));
                        if (owner != null && owner == taskQueue) owner.exported(snapshot.outcome(), now());
                        if (snapshot.outcome().successful() && owner == null) discardRecovery();
                        if (snapshot.outcome().successful()) io.execute(() -> {
                            try { recoveryStore.cleanupCompleted(ctx.files.directory()); }
                            catch (IOException e) { LOGGER.warn("Cannot clean completed recovery pages", e); }
                        });
                    });
                });
            }
        };
        engine = resumed == null ? new CaptureEngine(command, settings, sink, server, started)
                : CaptureEngine.resume(command, settings, sink, server, started, resumed.startedAt(),
                        resumed.page(), resumed.total(), recoveredPages);
        recoveredPages = java.util.List.of();
        config.remember(command.command()); saveConfig();
        setFeedback("status.started");
        playConfiguredSound(runConfig.startSound);
        noticeKey("notice.capturing", command.command());
        if (send) sendLookup(command.command());
        return true;
    }
    private void sendLookup(String command) {
        if (LookupCommand.parse(command).isEmpty()) {
            if (active()) engine.endKey(CaptureEngine.Outcome.MISMATCH, "reason.invalid_page_command", now());
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            disconnected("reason.no_connection");
            return;
        }
        ownCommand = true;
        try { mc.getConnection().sendCommand(command); }
        finally { ownCommand = false; }
    }
    private void queue(Context ctx, IoAction action) {
        io.execute(() -> {
            if (ctx.failed) return;
            try { action.run(); }
            catch (Exception error) {
                ctx.failed = true;
                try { ctx.files.close(); } catch (IOException closeError) { error.addSuppressed(closeError); }
                LOGGER.error(tr("notice.write_error"), error);
                Minecraft.getInstance().execute(() -> {
                    if (context == ctx) {
                        if (queueActive()) taskQueue.cancel();
                        if (active()) engine.endKey(CaptureEngine.Outcome.STORAGE_ERROR, "reason.storage_error", now());
                        saving = false;
                        setFeedback("notice.storage_error");
                    }
                    noticeKey("notice.save_failed");
                });
            }
        });
    }
    private void playConfiguredSound(String id) {
        if (id == null || id.isBlank()) return;
        try {
            var sound = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(Identifier.parse(id));
            Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(sound, 1.0f, 1.0f));
        } catch (RuntimeException e) { LOGGER.warn("Cannot play CoreTrace sound: {}", id, e); }
    }
    public void previewSound(String id) {
        stopPreview();
        if (id == null || id.isBlank()) return;
        preview = net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvent.createVariableRangeEvent(Identifier.parse(id)), 1.0f, 1.0f);
        Minecraft.getInstance().getSoundManager().play(preview);
    }
    public void stopPreview() {
        if (preview != null) Minecraft.getInstance().getSoundManager().stop(preview);
        preview = null;
    }
    public void saveConfig() {
        try { config.save(configPath); }
        catch (IOException e) { noticeKey("notice.config_save", e.getMessage()); LOGGER.warn("Config", e); }
    }
    public void togglePause() { if (active()) engine.togglePause(now()); }
    public void cancel() {
        if (queueActive()) taskQueue.cancel();
        if (active()) engine.endKey(CaptureEngine.Outcome.CANCELLED, "reason.cancelled", now());
        discardRecovery();
    }
    public void openExports() {
        try { Files.createDirectories(exports); openDirectory(exports); }
        catch (Exception e) { folderError(exports, e); }
    }
    public void openLast() {
        if (lastExportFile != null && Files.isRegularFile(lastExportFile)) {
            openDirectory(lastExportFile.getParent()); return;
        }
        // Recover exports from earlier versions, which did not persist the last path.
        io.execute(() -> {
            Path found = null;
            try {
                if (Files.isDirectory(exports)) try (var paths = Files.walk(exports, 2)) {
                    found = paths.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".csv"))
                            .sorted(java.util.Comparator.comparingLong(CoreTraceClient::modifiedAt).reversed()
                                    .thenComparing(p -> p.getFileName().toString())).findFirst().orElse(null);
                }
                Path file = found;
                Minecraft.getInstance().execute(() -> {
                    if (file == null) { openExports(); return; }
                    lastExportFile = file; lastDirectory = file.getParent();
                    config.lastExportFile = file.toString(); saveConfig(); openDirectory(lastDirectory);
                });
            } catch (IOException e) { Minecraft.getInstance().execute(() -> folderError(exports, e)); }
        });
    }
    private static long modifiedAt(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException e) { return Long.MIN_VALUE; }
    }
    private void openDirectory(Path directory) {
        Path absolute = directory.toAbsolutePath().normalize();
        try {
            if (!Files.isDirectory(absolute)) throw new IOException("Directory does not exist: " + absolute);
            // Uses the game's native SDL backend; works even when Java runs in headless mode.
            if (org.lwjgl.sdl.SDLMisc.SDL_OpenURL(absolute.toUri().toASCIIString())) return;
            if (System.getProperty("os.name", "").startsWith("Windows")) {
                String root = System.getenv("SystemRoot");
                Path explorer = Path.of(root == null ? "C:\\Windows" : root, "explorer.exe");
                new ProcessBuilder(explorer.toString(), absolute.toString()).start();
            } else throw new IOException("SDL could not open the folder");
        } catch (Exception e) { folderError(absolute, e); }
    }
    private void folderError(Path path, Exception error) {
        LOGGER.error("Cannot open export folder {}", path, error);
        noticeKey("notice.folder_failed", path);
    }
    public Language language() { return config == null ? Language.SPANISH : config.selectedLanguage(); }
    public String tr(String key, Object... args) {
        Object[] translated = java.util.Arrays.stream(args)
                .map(value -> value instanceof CaptureEngine.Outcome o ? Report.outcome(o, language()) : value).toArray();
        return Translations.text(language(), key, translated);
    }
    public void changeLanguage() { config.language = language().next().code(); saveConfig(); }
    private void setFeedback(String key, Object... args) { feedbackKey = key; feedbackArgs = args.clone(); }
    private String feedback() { return tr(feedbackKey, feedbackArgs); }
    public void noticeKey(String key, Object... args) { setFeedback(key, args); displayNotice(tr(key, args)); }
    private void displayNotice(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[CoreTrace] " + message));
        else LOGGER.info("[CoreTrace] {}", message);
    }
    public boolean queueActive() { return taskQueue != null && taskQueue.active(); }
    public boolean busy() { return active() || saving || queueActive() || restoring || resumeCountdown != null; }
    public boolean startTasks() {
        if (busy() || now() < readyAfter) { noticeKey("notice.busy"); return false; }
        var originals = java.util.List.copyOf(config.tasks);
        var tasks = config.copy().tasks;
        try {
            tasks.forEach(TaskDefinition::validate);
            for (var task : tasks) {
                Config effective = task.effective(config);
                task.universalSettings = false; task.taskSettings = effective; task.csvSettings = effective.captureCopy();
            }
            createTaskQueue(tasks, config.taskDelayMs, config.queueFinishSound, originals);
        } catch (IllegalArgumentException e) { noticeKey("queue.invalid"); return false; }
        recovery = null; recoveryWaiting = false;
        playConfiguredSound(config.queueStartSound);
        taskQueue.start();
        return queueActive();
    }
    private void createTaskQueue(java.util.List<TaskDefinition> tasks, int delay, String finishSound,
            java.util.List<TaskDefinition> originals) {
        taskQueue = new TaskQueue(tasks, delay, new TaskQueue.Host() {
            public boolean start(TaskDefinition task) {
                int current = taskQueue.position() - 1;
                return begin(LookupCommand.parse(task.command).orElseThrow(), true, task.effective(config),
                        originals == null ? task : originals.get(current));
            }
            public boolean command(String command) {
                var mc = Minecraft.getInstance();
                if (mc.getConnection() == null || mc.player == null) { noticeKey("queue.command_failed"); return false; }
                ownCommand = true; commandCancelled = false;
                try { mc.getConnection().sendCommand(command); if (commandCancelled) noticeKey("queue.command_failed"); return !commandCancelled; }
                finally { ownCommand = false; }
            }
            public void finished() { playConfiguredSound(finishSound); noticeKey("queue.finished"); }
            public boolean checkpoint(TaskQueue.State state) {
                if (state.phase() == TaskQueue.Phase.STOPPED) recovery = null;
                else {
                    var capture = recovery != null && recovery.queue() != null && recovery.queue().index() == state.index()
                            && state.phase() == TaskQueue.Phase.CAPTURE ? recovery.capture() : null;
                    recovery = new RecoveryStore.State(serverKey(), capture, state, finishSound);
                }
                return persistRecovery(true);
            }
        });
    }
    public boolean active() { return engine != null && engine.active(); }
    public CaptureEngine engine() { return engine; }
    public Config config() { return config; }
    public String status() {
        if (resumeCountdown != null) return tr("recovery.countdown", resumeCountdown.seconds(now()));
        if (restoring) return tr("recovery.loading");
        if (queueActive()) return tr("queue.progress", taskQueue.position(), taskQueue.size()) + " · " + (active() ? engine.detail(language()) : feedback());
        if (active()) return engine.detail(language());
        if (now() < readyAfter) return tr("status.cooldown", (readyAfter - now()) / 1000 + 1, feedback());
        return feedback();
    }
    public String lastQuery() { return config.recentQueries.isEmpty() ? "co l a:-block u:jose t:3d" : config.recentQueries.getFirst(); }
    private String serverKey() {
        var mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null) return mc.getCurrentServer().ip.toLowerCase(java.util.Locale.ROOT);
        if (mc.getSingleplayerServer() != null)
            return "local:" + mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().normalize();
        return "";
    }
    private void checkpointCapture(int page, int total, CaptureEngine.Page completed) {
        if (recovery == null || recovery.capture() == null || context == null) return;
        recovery = recovery.withCapture(recovery.capture().at(page, total));
        var saved = recovery;
        queue(context, () -> {
            if (completed != null) recoveryStore.writePage(saved.capture(), completed);
            recoveryStore.save(saved);
        });
    }
    private boolean persistRecovery(boolean wait) {
        var state = recovery;
        try {
            if (wait) io.submit(() -> { recoveryStore.save(state); return true; }).get();
            else io.execute(() -> {
                try { recoveryStore.save(state); }
                catch (IOException e) {
                    LOGGER.error("Cannot persist recovery", e);
                    Minecraft.getInstance().execute(() -> noticeKey("recovery.save_failed"));
                }
            });
            return true;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            LOGGER.error("Cannot persist recovery", e); noticeKey("recovery.save_failed"); return false;
        }
    }
    private void disconnected(String reason) {
        ++recoveryGeneration; restoring = false; resumeCountdown = null;
        if (queueActive() && recovery != null) recovery = recovery.withQueue(taskQueue.snapshot(now()));
        if (active()) checkpointCapture(engine.expectedPage(), engine.totalPages(), null);
        else if (recovery != null) persistRecovery(false);
        recoveryWaiting = recovery != null;
        if (queueActive()) taskQueue.cancel();
        if (active()) engine.endKey(CaptureEngine.Outcome.DISCONNECTED, reason, now());
    }
    public boolean hasRecovery() { return recovery != null; }
    public void discardRecovery() {
        ++recoveryGeneration; recoveryWaiting = false; restoring = false; resumeCountdown = null;
        resumeStarting = null; recoveredPages = java.util.List.of(); recovery = null; persistRecovery(false);
    }
    private void reconnectTick() {
        if (reconnectPlan == null) return;
        var mc = Minecraft.getInstance();
        if (!config.autoReconnect || reconnectServer == null) { reconnectPlan = null; return; }
        if (mc.player != null) return;
        var screen = mc.gui.screen();
        if (screen instanceof BaseScreen) { reconnectFailureScreen = null; return; }
        if (screen instanceof net.minecraft.client.gui.screens.ConnectScreen) return;
        if (!(screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen)) {
            reconnectPlan = null; reconnectFailureScreen = null; return;
        }
        if (screen != reconnectFailureScreen) {
            reconnectFailureScreen = screen;
            reconnectPlan.failed(now());
        }
        if (!reconnectPlan.attempt(now())) return;
        LOGGER.info("CoreTrace reconnect attempt {} / {}", reconnectPlan.attempts(), config.reconnectAttempts);
        net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(new net.minecraft.client.gui.screens.TitleScreen()),
                mc, net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(reconnectServer.ip),
                reconnectServer, false, null);
    }
    private void recoveryTick() {
        var mc = Minecraft.getInstance();
        if (!config.autoResume || !recoveryWaiting || recovery == null || mc.player == null
                || mc.getConnection() == null || !serverKey().equals(recovery.server())) { resumeCountdown = null; return; }
        if (active() || queueActive() || saving || restoring || now() < readyAfter) return;
        if (resumeCountdown == null || resumeCountdown.delay() != config.autoResumeDelayMs) {
            resumeCountdown = new ResumeCountdown(now(), config.autoResumeDelayMs);
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[CoreTrace] " + tr("recovery.warning",
                    config.autoResumeDelayMs)).withStyle(net.minecraft.ChatFormatting.RED));
        }
        if (resumeCountdown.soundDue(now())) playConfiguredSound("minecraft:block.note_block.pling");
        if (resumeCountdown.remaining(now()) == 0) restoreRecovery();
    }
    private void restoreRecovery() {
        var state = recovery;
        int generation = ++recoveryGeneration;
        resumeCountdown = null; restoring = true;
        io.execute(() -> {
            try {
                var pages = state.capture() == null ? java.util.List.<CaptureEngine.Page>of() : recoveryStore.pages(state.capture());
                Minecraft.getInstance().execute(() -> {
                    if (generation != recoveryGeneration) return;
                    restoring = false;
                    if (!config.autoResume || !recoveryWaiting || Minecraft.getInstance().getConnection() == null
                            || !serverKey().equals(state.server())) return;
                    recoveryWaiting = false;
                    resumeStarting = state.capture(); recoveredPages = pages;
                    try {
                        if (state.queue() != null) {
                            var q = state.queue();
                            createTaskQueue(q.tasks(), q.delay(), state.finishSound(), null);
                            taskQueue.restore(q, now());
                        } else begin(LookupCommand.parse(state.capture().command()).orElseThrow(), true,
                                state.capture().config().captureCopy(), null);
                    } catch (Exception e) {
                        if (queueActive()) taskQueue.cancel();
                        LOGGER.error("Recovery failed", e); noticeKey("recovery.load_failed");
                    } finally { resumeStarting = null; recoveredPages = java.util.List.of(); }
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> {
                    if (generation != recoveryGeneration) return;
                    restoring = false; recoveryWaiting = false;
                    LOGGER.error("Recovery pages could not be loaded", e); noticeKey("recovery.load_failed");
                });
            }
        });
    }
    private static long now() { return System.currentTimeMillis(); }
}
