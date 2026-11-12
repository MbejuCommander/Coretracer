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
    private SmartDelayStore delayStore = new SmartDelayStore();
    private Path delayStorePath;
    private SmartDelay smartDelay;
    private final CaptureEta eta = new CaptureEta();
    private long etaRefreshAt, etaFinishedUntil;
    private String etaText = "";
    private boolean etaComplete;
    private long delaySavedAt;
    private Path configPath, exports;
    private CaptureEngine engine;
    private TaskQueue taskQueue;
    private boolean queueCaptureAwaitingCommand;
    private QueueExcel queueExcel;
    private Config captureConfig;
    private boolean saving;
    private Context context;
    private boolean ownCommand, openPending, commandCancelled;
    private String feedbackKey = "status.ready";
    private Object[] feedbackArgs = new Object[0];
    private Path lastDirectory;
    private Path lastExportFile;
    private volatile Path lastErrorReport;
    private net.minecraft.client.resources.sounds.SoundInstance preview;
    private KeyMapping key;
    private long readyAfter;
    private RecoveryStore recoveryStore;
    private RecoveryStore.State recovery;
    private RecoveryStore.Capture resumeStarting;
    private java.util.List<CaptureEngine.Page> recoveredPages = java.util.List.of();
    private ResumeCountdown resumeCountdown;
    private long resumeZeroAt = -1;
    private boolean recoveryWaiting, restoring;
    private final ResumePolicy resumePolicy = new ResumePolicy();
    private boolean manualResume;
    private final RecoveryNotice recoveryAddressNotice = new RecoveryNotice();
    private int recoveryGeneration;
    private net.minecraft.client.multiplayer.ServerData reconnectServer;
    private ReconnectPlan reconnectPlan;
    private net.minecraft.client.gui.screens.Screen reconnectFailureScreen;
    private static final class Context {
        final SessionFiles files;
        volatile boolean failed;
        boolean cancelRequested;
        boolean skipCurrent;
        boolean checkingRecovery;
        boolean stopQueueAfterSave;
        final java.util.concurrent.atomic.AtomicInteger work = new java.util.concurrent.atomic.AtomicInteger();
        private final java.util.ArrayDeque<String> events = new java.util.ArrayDeque<>();
        Context(Path path, Language language, Config config) { files = new SessionFiles(path, language, config); }
        synchronized void event(String code, int page) {
            if (events.size() == 16) events.removeFirst();
            events.addLast(code + ".page_" + Math.max(0, page));
        }
        synchronized java.util.List<String> events() { return java.util.List.copyOf(events); }
    }
    @FunctionalInterface private interface IoAction { void run() throws IOException; }

    @Override public void onInitializeClient() {
        INSTANCE = this;
        configPath = FabricLoader.getInstance().getConfigDir().resolve("coretrace.json");
        exports = FabricLoader.getInstance().getGameDir().resolve("coretrace/exports").toAbsolutePath();
        try { config = Config.load(configPath); }
        catch (IOException e) { config = new Config(); setFeedback("notice.config_load"); LOGGER.warn(tr("notice.config_load"), e); }
        delayStorePath = exports.getParent().resolve("smart-delay.json");
        try { delayStore = SmartDelayStore.load(delayStorePath); }
        catch (IOException e) { LOGGER.warn("Cannot load Smart Delay learning", e); }
        recoveryStore = new RecoveryStore(exports.getParent().resolve("recovery.json"), exports);
        try { recovery = recoveryStore.load(); recoveryWaiting = recovery != null; }
        catch (IOException e) { LOGGER.warn("Cannot load CoreTrace recovery", e); setFeedback("recovery.load_failed"); }
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("coretrace", "resume_countdown"), (graphics, delta) -> {
                    var mc = Minecraft.getInstance();
                    var countdown = active() && engine.inspectionCountdown() != null ? engine.inspectionCountdown() : resumeCountdown;
                if (countdown != null && mc.player != null) {
                        graphics.centeredText(mc.font, tr("recovery.countdown", active() && engine.inspectionCountdown() != null
                                ? engine.inspectionSeconds(now()) : countdown.seconds(now())),
                                mc.getWindow().getGuiScaledWidth() / 2, mc.getWindow().getGuiScaledHeight() / 2 + 18, 0xFFFF5555);
                    }
                });
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("coretrace", "capture_eta"), (graphics, delta) -> {
                    var mc = Minecraft.getInstance();
                    if (mc.player != null && config.showEta && captureConfig != null && captureConfig.showEta
                            && (active() || saving || now() < etaFinishedUntil)) {
                        if (now() >= etaRefreshAt) { etaText = captureEtaText(); etaRefreshAt = now() + 1000; }
                        graphics.centeredText(mc.font, etaText, mc.getWindow().getGuiScaledWidth() / 2,
                                mc.getWindow().getGuiScaledHeight() - 72, 0xFFE3EFF5);
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
        net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (level.isClientSide() && active()) engine.blockInteraction(now());
            return net.minecraft.world.InteractionResult.PASS;
        });
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() && active()) engine.blockInteraction(now());
            return net.minecraft.world.InteractionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) openPending = true;
            if (openPending) { openPending = false; client.gui.setScreen(new MainScreen(client.gui.screen(), null)); }
            if (active()) {
                if (engine.paused() || engine.recovering()) { eta.interrupt(); if (smartDelay != null) smartDelay.interrupt(); }
                var countdown = engine.inspectionCountdown();
                if (countdown != null && !engine.paused() && countdown.soundDue(now())) playConfiguredSound("minecraft:entity.experience_orb.pickup");
                engine.tick(now());
            }
            if (queueActive()) taskQueue.tick(now());
            reconnectTick();
            recoveryTick();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            reconnectServer = client.getCurrentServer();
            reconnectPlan = null; reconnectFailureScreen = null;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            var details = handler.getConnection().getDisconnectionDetails();
            if (active() && smartDelay != null && details != null
                    && smartDelay.disconnected(details.reason().getString(), captureConfig.smartDelayKickMessage, now())) {
                readyAfter = Math.max(readyAfter, smartDelay.state().cooldownUntil);
                LOGGER.info("Spam kick detected; Smart Delay increased to {} ms", smartDelay.delay());
            }
            saveDelayLearning();
            disconnected("reason.disconnected");
            if (config.autoReconnect && reconnectServer != null && !reconnectServer.isRealm()) {
                reconnectPlan = new ReconnectPlan(config.reconnectDelayMs, config.reconnectAttempts);
                reconnectFailureScreen = null;
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            saveDelayLearning();
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
                .then(literal("pause").executes(c -> {
                    if (queueActive() && !queuePaused()) toggleQueuePause();
                    else if (!queueActive() && active() && !engine.paused()) togglePause(); return 1;
                }))
                .then(literal("resume").executes(c -> {
                    if (queuePaused()) toggleQueuePause();
                    else if (!queueActive()) resumeManually(); return 1;
                }))
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
        RecoveryStore.Capture resumed = savedResume;
        if (resumed != null && runConfig.smartCsvEnabled()) noticeKey("smart.refresh");
        Path path;
        try { path = resumed != null ? recoveryStore.directory(resumed) : CsvFileNames.reserveDirectory(exports, runConfig.csvFileName,
                NAME.format(Instant.ofEpochMilli(started)) + "-" + UUID.randomUUID().toString().substring(0, 8)); }
        catch (IOException e) { noticeKey("notice.save_failed"); return false; }
        captureConfig = runConfig;
        smartDelay = runConfig.smartDelay ? new SmartDelay(delayStore.state(runConfig.smartDelayPerServer, server), runConfig.delayMs, runConfig.smartDelayMinimumMs, runConfig.smartDelayWindowSeconds, runConfig.smartDelayHoldSeconds) : null;
        eta.interrupt(); etaComplete = false; etaFinishedUntil = 0; etaRefreshAt = 0;
        var settings = runConfig.settings();
        Context ctx = new Context(path, settings.language(), runConfig); context = ctx;
        ctx.checkingRecovery = resumed != null;
        ctx.event(resumed == null ? "capture_started" : "resume_started",
                resumed == null ? command.firstPage() : resumed.page());
        if (resumed == null) { resumePolicy.reset(); manualResume = false; recoveryAddressNotice.cancel(); }
        TaskQueue owner = task == null ? null : taskQueue;
        QueueExcel workbook = owner == null ? null : queueExcel;
        int excelTaskIndex = owner == null ? -1 : owner.position() - 1;
        recoveryWaiting = false; resumeCountdown = null;
        if (command.query()) {
            var capture = resumed != null ? resumed : new RecoveryStore.Capture(path.getFileName().toString(),
                    command.command(), started, 1, 0, runConfig.captureCopy());
            recovery = new RecoveryStore.State(server, capture, owner == null ? null : owner.snapshot(now()),
                    recovery == null ? "" : recovery.finishSound(), workbook == null ? null : workbook.directoryName(),
                    workbook == null ? null : workbook.fileName());
        } else recovery = null;
        RecoveryStore.State initialRecovery = recovery;
        queue(ctx, () -> {
            if (resumed != null) {
                ctx.files.resume(resumed.page());
                ctx.files.restart(command.command(), command.firstPage(), resumed.page());
            }
            else ctx.files.begin(command.command(), server, started);
            recoveryStore.save(initialRecovery);
        });
        CaptureEngine.Sink sink = new CaptureEngine.Sink() {
            @Override public void send(String text) {
                checkpointCapture(engine.expectedPage(), engine.totalPages(), null);
                if (smartDelay != null && LookupCommand.parse(text).filter(c -> !c.query()).isPresent()) smartDelay.pageCommand();
                sendLookup(text);
            }
            @Override public java.util.List<MessageData> prefixBuffer() {
                return new PrefixJournal.Buffer(ctx.files.directory(), action -> queue(ctx, action::run));
            }
            @Override public java.util.List<CaptureEngine.Page> history(int first) { return ctx.files.history(first); }
            @Override public boolean ready() { return !ctx.failed && ctx.work.get() < 4; }
            @Override public void line(int page, MessageData m) { /* Written as a bounded page batch. */ }
            @Override public void restartExport() {
                checkpointCapture(engine.nextRecoveryPage(), engine.totalPages(), null);
                queue(ctx, () -> ctx.files.restart(command.command(), command.firstPage(), engine.nextRecoveryPage()));
                noticeKey("smart.refresh");
            }
            @Override public void replaceFirst(CaptureEngine.Page page, int nextPage,
                                               java.util.List<MessageData> newEntries, int offset) {
                queue(ctx, () -> ctx.files.replaceFirst(page, command.command(), nextPage, newEntries, offset));
                checkpointCapture(engine.totalPages() > 0 ? Math.min(nextPage, engine.totalPages()) : nextPage, engine.totalPages(), null);
            }
            @Override public void recovering(int page) {
                checkpointCapture(page, engine.totalPages(), null);
                playConfiguredSound("minecraft:entity.villager.no");
                playConfiguredSound("minecraft:entity.experience_orb.pickup");
                mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[CoreTrace] " + tr("inspect.warning", page, runConfig.autoResumeDelayMs))
                        .withStyle(net.minecraft.ChatFormatting.RED));
            }
            @Override public void recoveringChangedResults(int page) {
                checkpointCapture(page, engine.totalPages(), null);
                playConfiguredSound("minecraft:entity.villager.no");
                playConfiguredSound("minecraft:entity.experience_orb.pickup");
                mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[CoreTrace] " + tr("smart.shift_warning", page, runConfig.autoResumeDelayMs, runConfig.smartShiftRetries, runConfig.smartStablePages))
                        .withStyle(net.minecraft.ChatFormatting.RED));
            }
            @Override public void restarted() {
                playConfiguredSound("minecraft:entity.villager.yes");
                noticeKey("inspect.resuming");
            }
            @Override public void page(CaptureEngine.Page p) {
                ctx.event(p.confirmed() ? "page_confirmed" : "page_partial", p.number());
                if (ctx.checkingRecovery && p.confirmed()) { ctx.checkingRecovery = false; resumePolicy.reset(); manualResume = false; }
                if (p.confirmed()) {
                    eta.page(now());
                    if (smartDelay != null) {
                        smartDelay.confirmed(now());
                        if (now() - delaySavedAt >= 30000) saveDelayLearning();
                    }
                }
                queue(ctx, () -> ctx.files.page(p));
                checkpointCapture(p.confirmed() && p.number() < engine.totalPages() ? p.number() + 1 : p.number(), engine.totalPages(), null);
            }
            @Override public void finish(CaptureEngine.Snapshot snapshot) {
                ctx.event("capture_finished", engine.nextRecoveryPage());
                etaComplete = false; etaFinishedUntil = 0; etaRefreshAt = 0;
                saveDelayLearning();
                DiagnosticReport.Incident incident = (DiagnosticReport.reportable(snapshot.outcome(),
                        ctx.checkingRecovery, resumePolicy.exhausted())
                        || snapshot.outcome() == CaptureEngine.Outcome.LIMIT
                        && "reason.memory_limit".equals(engine.diagnosticCode()))
                        ? incident("capture", engine.diagnosticCode(), snapshot.pages().size(),
                                snapshot.totalPages(), ctx.events(), null) : null;
                if (!snapshot.outcome().successful()
                        && snapshot.outcome() != CaptureEngine.Outcome.DISCONNECTED)
                    readyAfter = now() + runConfig.timeoutMs;
                saving = true;
                setFeedback("status.saving");
                queue(ctx, () -> {
                    ctx.files.finish(snapshot);
                    // Publish Excel BEFORE advancing the queue or discarding recovery pages.
                    // Failure leaves both the recovery pointer and the last valid workbook intact.
                    if (workbook != null) workbook.saveTask(excelTaskIndex, runConfig.csvFileName, ctx.files.csvPaths());
                    if (incident != null) try {
                        Path report = DiagnosticReport.write(ctx.files.directory(), incident);
                        mc.execute(() -> lastErrorReport = report);
                    } catch (IOException reportError) { LOGGER.warn("Cannot save CoreTrace error report", reportError); }
                    mc.execute(() -> {
                        saving = false;
                        etaComplete = snapshot.outcome().successful();
                        etaFinishedUntil = etaComplete ? now() + 3000 : 0; etaRefreshAt = 0;
                        if (ctx.checkingRecovery && !snapshot.outcome().successful()
                                && snapshot.outcome() != CaptureEngine.Outcome.CANCELLED) {
                            if (snapshot.outcome() == CaptureEngine.Outcome.MISMATCH) resumePolicy.stop();
                            recoveryWaiting = !resumePolicy.exhausted();
                            if (!recoveryWaiting && snapshot.outcome() != CaptureEngine.Outcome.MISMATCH)
                                noticeKey("recovery.attempts_exhausted");
                            readyAfter = Math.max(readyAfter, now() + 4000);
                        }
                        if (snapshot.outcome().successful()) { resumePolicy.reset(); manualResume = false; }
                        if (snapshot.outcome().successful() && runConfig.resetCsvNameAfterExport) {
                            if (task == null) config.csvFileName = CsvFileNames.afterExport(config.csvFileName, runConfig.csvFileName, true);
                            if (task != null && config.tasks.contains(task)) task.csvName = CsvFileNames.afterExport(task.csvName, runConfig.csvFileName, true);
                        }
                        lastExportFile = workbook == null ? ctx.files.lastCsvPath() : workbook.file();
                        lastDirectory = lastExportFile == null ? ctx.files.directory() : lastExportFile.getParent();
                        if (lastExportFile != null) {
                            config.lastExportFile = lastExportFile.toString();
                            saveConfig();
                        }
                        playConfiguredSound(runConfig.finishSound);
                        if (context == ctx) setFeedback("notice.finished", snapshot.outcome(), snapshot.pages().size());
                        displayNotice(tr("notice.finished", snapshot.outcome(), snapshot.pages().size()));
                        if (owner != null && owner == taskQueue) {
                            if (ctx.skipCurrent && snapshot.outcome() == CaptureEngine.Outcome.CANCELLED)
                                owner.skipCurrent(now(), runConfig.timeoutMs);
                            else owner.exported(snapshot.outcome(), now());
                        }
                        boolean savedCancellation = (snapshot.outcome() == CaptureEngine.Outcome.CANCELLED && ctx.cancelRequested)
                                || ctx.stopQueueAfterSave;
                        if (savedCancellation || (snapshot.outcome().successful() && (owner == null || ctx.stopQueueAfterSave)))
                            discardRecovery();
                        if (snapshot.outcome().successful() || savedCancellation || ctx.skipCurrent) io.execute(() -> {
                            try {
                                if (Files.isDirectory(ctx.files.directory()))
                                    recoveryStore.cleanupCompleted(ctx.files.directory());
                            } catch (IOException e) { LOGGER.warn("Cannot clean completed recovery pages", e); }
                        });
                    });
                });
            }
        };
        engine = resumed == null ? new CaptureEngine(command, settings, sink, server, started)
                : CaptureEngine.resume(command, settings, sink, server, started, resumed.startedAt(),
                        resumed.page(), resumed.total(), recoveredPages);
        if (resumed != null) engine.restorePageOffset(resumed.pageOffset());
        if (smartDelay != null) engine.configureDelay(smartDelay::delay);
        engine.configureInspectionRecovery(runConfig.inspectRecovery, runConfig.autoResumeDelayMs);
        engine.configureSmartStablePages(runConfig.smartStablePages);
        engine.configureSmartShiftRetries(runConfig.smartShiftRetries);
        recoveredPages = java.util.List.of();
        config.remember(command.command()); saveConfig();
        setFeedback("status.started");
        playConfiguredSound(runConfig.startSound);
        noticeKey("notice.capturing", command.command());
        if (send) sendLookup(command.command());
        else if (smartDelay != null) smartDelay.command(now());
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
        ownCommand = true; commandCancelled = false;
        try {
            mc.getConnection().sendCommand(command);
            if (smartDelay != null && !commandCancelled) smartDelay.command(now());
        }
        finally { ownCommand = false; }
    }
    private void queue(Context ctx, IoAction action) {
        ctx.work.incrementAndGet();
        io.execute(() -> {
            try { if (!ctx.failed) action.run(); }
            catch (Exception error) {
                ctx.failed = true;
                try { ctx.files.close(); } catch (IOException closeError) { error.addSuppressed(closeError); }
                try {
                    Path report = DiagnosticReport.write(ctx.files.directory(), new DiagnosticReport.Incident(
                            modVersion(), now(), "storage", "write_failed", 0, 0, 0, 0, 0, 0, 0,
                            false, ctx.events(), error));
                    Minecraft.getInstance().execute(() -> lastErrorReport = report);
                } catch (IOException reportError) { LOGGER.warn("Cannot save CoreTrace storage report", reportError); }
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
            } finally { ctx.work.decrementAndGet(); }
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
    public void factoryReset() {
        if (busy()) { noticeKey("reset.busy"); return; }
        Config fresh = config.factoryDefaults();
        try { fresh.save(configPath); }
        catch (IOException e) { noticeKey("notice.config_save", e.getMessage()); return; }
        config = fresh; delayStore = new SmartDelayStore(); smartDelay = null;
        saveDelayLearning(); stopPreview(); reconnectPlan = null;
        setFeedback("reset.done");
    }
    public void saveConfig() {
        try { config.save(configPath); }
        catch (IOException e) { noticeKey("notice.config_save", e.getMessage()); LOGGER.warn("Config", e); }
    }
    public void togglePause() { if (active()) { eta.interrupt(); if (smartDelay != null) smartDelay.interrupt(); engine.togglePause(now()); } }
    private String captureEtaText() {
        String value;
        if (etaComplete) value = "00:00:00";
        else if (saving) value = tr("eta.saving");
        else if (engine.paused()) value = tr("eta.paused");
        else if (engine.recovering()) value = tr("eta.recovering");
        else {
            long seconds = eta.seconds(engine.completedPages(), engine.totalPages());
            value = seconds < 0 ? tr("eta.calculating") : CaptureEta.time(seconds);
        }
        return "ETA " + value + " · " + CaptureEta.percent(engine.completedPages(), engine.totalPages(), etaComplete) + " %";
    }
    SmartDelay delayLearning(Config settings) {
        return new SmartDelay(SmartDelay.copy(delayStore.state(settings.smartDelayPerServer, serverKey())), settings.delayMs, settings.smartDelayMinimumMs, settings.smartDelayWindowSeconds, settings.smartDelayHoldSeconds);
    }
    void resetDelayLearning(Config settings) {
        delayStore.reset(settings.smartDelayPerServer, serverKey());
        if (active() && captureConfig.smartDelay && captureConfig.smartDelayPerServer == settings.smartDelayPerServer) {
            smartDelay = new SmartDelay(delayStore.state(settings.smartDelayPerServer, serverKey()), captureConfig.delayMs, captureConfig.smartDelayMinimumMs, captureConfig.smartDelayWindowSeconds, captureConfig.smartDelayHoldSeconds);
            engine.configureDelay(smartDelay::delay);
        }
        saveDelayLearning();
    }
    private void saveDelayLearning() {
        if (delayStorePath == null || io.isShutdown()) return;
        String json = delayStore.json(); delaySavedAt = now();
        io.execute(() -> {
            try { Files.createDirectories(delayStorePath.getParent()); SessionFiles.writeAtomic(delayStorePath, json); }
            catch (IOException e) { LOGGER.warn("Cannot save Smart Delay learning", e); }
        });
    }
    public boolean queuePaused() { return queueActive() && taskQueue.paused(); }
    public boolean canEditQueuePending() { return queuePaused() && !saving && !restoring; }
    public int queueCurrentIndex() { return queueActive() ? taskQueue.position() - 1 : -1; }
    public java.util.List<TaskDefinition> queueTasks() {
        return queueActive() ? taskQueue.snapshot(now()).tasks() : java.util.List.of();
    }
    public java.util.List<TaskDefinition> queuePendingTasks() {
        var result = new java.util.ArrayList<TaskDefinition>();
        if (queueActive()) for (TaskDefinition task : queueTasks().subList(taskQueue.position(), taskQueue.size()))
            result.add(task.copy());
        return result;
    }
    public boolean replacePendingTasks(java.util.List<TaskDefinition> pending) {
        if (!canEditQueuePending()) return false;
        try {
            var frozen = new java.util.ArrayList<TaskDefinition>();
            for (TaskDefinition item : pending) {
                TaskDefinition task = item.copy(); task.validate();
                Config effective = task.effective(config);
                task.universalSettings = false; task.taskSettings = effective;
                task.csvSettings = effective.captureCopy();
                frozen.add(task);
            }
            if (taskQueue.replacePending(frozen, now())) return true;
        } catch (IllegalArgumentException e) { LOGGER.warn("Invalid pending task edit", e); }
        noticeKey("queue.edit_failed"); return false;
    }
    public boolean canSkipCurrentTask() { return queueActive() && taskQueue.canSkipCurrent() && !saving; }
    public void toggleQueuePause() {
        if (!queueActive()) return;
        boolean pause = !taskQueue.paused();
        if (!taskQueue.setPaused(pause, now())) { noticeKey("recovery.save_failed"); return; }
        if (active()) {
            if (engine.paused() == !pause) engine.togglePause(now());
            if (!pause && queueCaptureAwaitingCommand) {
                queueCaptureAwaitingCommand = false;
                sendLookup(engine.command());
            }
        }
        noticeKey(pause ? "queue.paused" : "queue.resumed");
    }
    public void cancelCurrentTask() {
        if (!canSkipCurrentTask()) return;
        if (active()) {
            if (context != null) context.skipCurrent = true;
            engine.endKey(CaptureEngine.Outcome.CANCELLED, "queue.task_cancelled", now());
        } else taskQueue.skipCurrent(now());
    }
    public void cancel() {
        queueCaptureAwaitingCommand = false;
        if (queueActive()) taskQueue.cancel();
        if (active()) {
            if (context != null) context.cancelRequested = true;
            engine.endKey(CaptureEngine.Outcome.CANCELLED, "reason.cancelled", now());
        } else if (saving) {
            if (context != null) context.stopQueueAfterSave = true;
        } else discardRecovery();
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
                            .filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).matches(".*\\.(csv|xlsx)"))
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
    public boolean hasErrorReport() { return lastErrorReport != null && Files.isRegularFile(lastErrorReport); }
    public void openLastErrorReport() {
        io.execute(() -> {
            Path found = null;
            try {
                if (hasErrorReport()) found = lastErrorReport;
                else if (Files.isDirectory(exports)) try (var paths = Files.walk(exports, 2)) {
                    found = paths.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().matches("error-report-[0-9]{8}-[0-9]{6}-[0-9]{3}-[a-f0-9]{8}\\.txt"))
                            .sorted(java.util.Comparator.comparingLong(CoreTraceClient::modifiedAt).reversed()).findFirst().orElse(null);
                }
                Path report = found;
                Minecraft.getInstance().execute(() -> {
                    if (report == null) { noticeKey("report.none"); return; }
                    lastErrorReport = report;
                    try {
                        if (org.lwjgl.sdl.SDLMisc.SDL_OpenURL(report.toUri().toASCIIString())) return;
                        openDirectory(report.getParent());
                    } catch (Exception e) { folderError(report, e); }
                });
            } catch (IOException e) { Minecraft.getInstance().execute(() -> folderError(exports, e)); }
        });
    }
    private static String modVersion() {
        return FabricLoader.getInstance().getModContainer("coretrace")
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
    }
    private DiagnosticReport.Incident incident(String category, String code, int pages, int total,
                                                java.util.List<String> events, Throwable error) {
        return new DiagnosticReport.Incident(modVersion(), now(), category, code, pages, total,
                queueActive() ? taskQueue.position() : 0, queueActive() ? taskQueue.size() : 0,
                resumePolicy.attempts(), smartDelay == null ? 0 : smartDelay.state().kickCount,
                smartDelay == null ? 0 : smartDelay.delay(), captureConfig != null && captureConfig.smartCsvEnabled(),
                events, error);
    }
    private void reportOperationFailure(String category, String code, Throwable error) {
        int savedPages = active() ? engine.completedPages() : recovery != null && recovery.capture() != null
                ? Math.max(0, recovery.capture().page() - 1) : 0;
        int totalPages = active() ? engine.totalPages() : recovery != null && recovery.capture() != null
                ? recovery.capture().total() : 0;
        DiagnosticReport.Incident report = incident(category, code,
                savedPages, totalPages,
                context == null ? java.util.List.of() : context.events(), error);
        Path directory = queueExcel != null ? queueExcel.file().getParent() : exports;
        io.execute(() -> {
            try {
                Path file = DiagnosticReport.write(directory, report);
                Minecraft.getInstance().execute(() -> lastErrorReport = file);
            } catch (IOException writeError) { LOGGER.warn("Cannot save CoreTrace error report", writeError); }
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
            queueExcel = config.queueExcel ? QueueExcel.createRun(exports, config.queueExcelName,
                    "queue-" + NAME.format(Instant.ofEpochMilli(now()))
                            + "-" + UUID.randomUUID().toString().substring(0, 8)) : null;
            createTaskQueue(tasks, config.taskDelayMs, config.queueFinishSound, originals);
        } catch (IllegalArgumentException e) { noticeKey("queue.invalid"); return false; }
        catch (IOException e) { LOGGER.error("Cannot create Excel queue", e); noticeKey("notice.save_failed"); return false; }
        recovery = null; recoveryWaiting = false;
        playConfiguredSound(config.queueStartSound);
        taskQueue.start();
        return queueActive();
    }
    private void createTaskQueue(java.util.List<TaskDefinition> tasks, int delay, String finishSound,
            java.util.List<TaskDefinition> originals) {
        var originalsByTask = new java.util.IdentityHashMap<TaskDefinition, TaskDefinition>();
        if (originals != null) for (int i = 0; i < Math.min(tasks.size(), originals.size()); i++)
            originalsByTask.put(tasks.get(i), originals.get(i));
        taskQueue = new TaskQueue(tasks, delay, new TaskQueue.Host() {
            public boolean start(TaskDefinition task) {
                int current = taskQueue.position() - 1;
                boolean deferred = taskQueue.paused();
                boolean started = begin(LookupCommand.parse(task.command).orElseThrow(), !deferred, task.effective(config),
                        originalsByTask.getOrDefault(task, task));
                if (started && deferred) {
                    queueCaptureAwaitingCommand = true;
                    engine.togglePause(now());
                }
                if (!started) reportOperationFailure("queue", "task_start_failed", null);
                return started;
            }
            public boolean command(String command) {
                var mc = Minecraft.getInstance();
                if (mc.getConnection() == null || mc.player == null) {
                    noticeKey("queue.command_failed"); reportOperationFailure("queue", "custom_command_failed", null); return false;
                }
                ownCommand = true; commandCancelled = false;
                try {
                    mc.getConnection().sendCommand(command);
                    if (commandCancelled) { noticeKey("queue.command_failed"); reportOperationFailure("queue", "custom_command_failed", null); }
                    return !commandCancelled;
                }
                finally { ownCommand = false; }
            }
            @Override public boolean readyForNextTask() { return now() >= readyAfter; }
            public void finished() {
                finished(true);
            }
            @Override public void finished(boolean complete) {
                playConfiguredSound(finishSound); noticeKey(complete ? "queue.finished" : "queue.finished_partial");
                if (!complete) return;
                QueueExcel completed = queueExcel;
                if (completed != null) io.execute(() -> {
                    try {
                        RecoveryStore.State pending = recoveryStore.load();
                        if (pending == null || !completed.directoryName().equals(pending.excelDirectory()))
                            completed.cleanupSources();
                    } catch (IOException e) {
                        LOGGER.warn("Cannot clean completed Excel queue inputs", e);
                        Minecraft.getInstance().execute(() -> noticeKey("queue.excel_cleanup_failed"));
                    }
                });
            }
            public boolean checkpoint(TaskQueue.State state) {
                if (state.phase() == TaskQueue.Phase.STOPPED) recovery = null;
                else {
                    var capture = recovery != null && recovery.queue() != null && recovery.queue().index() == state.index()
                            && state.phase() == TaskQueue.Phase.CAPTURE ? recovery.capture() : null;
                    recovery = new RecoveryStore.State(serverKey(), capture, state, finishSound,
                            queueExcel == null ? null : queueExcel.directoryName(),
                            queueExcel == null ? null : queueExcel.fileName());
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
        if (queueActive()) return tr("queue.progress", taskQueue.position(), taskQueue.size()) + " · "
                + (taskQueue.paused() ? tr("queue.paused") : active() ? engine.detail(language()) : feedback());
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
        recovery = recovery.withCapture(recovery.capture().at(page, total, engine == null ? recovery.capture().pageOffset() : engine.recoveryPageOffset()));
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
        if (context != null && active()) context.event("disconnected", engine.nextRecoveryPage());
        queueCaptureAwaitingCommand = false;
        ++recoveryGeneration; restoring = false; resumeCountdown = null; recoveryAddressNotice.cancel();
        if (queueActive() && recovery != null) recovery = recovery.withQueue(taskQueue.snapshot(now()));
        if (active()) checkpointCapture(engine.nextRecoveryPage(), engine.totalPages(), null);
        else if (recovery != null) persistRecovery(false);
        recoveryWaiting = recovery != null;
        if (queueActive()) taskQueue.cancel();
        if (active()) engine.endKey(CaptureEngine.Outcome.DISCONNECTED, reason, now());
    }
    public boolean hasRecovery() { return recovery != null; }
    public void discardRecovery() {
        recoveryAddressNotice.cancel();
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
    private boolean recoveryServerAllowed() {
        return ResumePolicy.allows(recovery, serverKey(), config.resumeAcrossServers);
    }
    public boolean resumable() { return !queueActive() && (active() ? engine.paused() : recovery != null); }
    public void resumeManually() {
        if (active()) { if (engine.paused()) togglePause(); return; }
        if (saving || restoring || queueActive() || recovery == null) return;
        if (!recoveryServerAllowed()) { noticeKey("recovery.address_blocked"); return; }
        manualResume = true; recoveryWaiting = true; resumePolicy.reset();
    }
    private void recoveryTick() {
        var mc = Minecraft.getInstance();
        if (recoveryAddressNotice.poll(now())) {
            if (config.resumeAcrossServers && mc.player != null && recovery != null)
                mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[CoreTrace] " + tr("recovery.cross_server_notice"))
                        .withStyle(net.minecraft.ChatFormatting.RED));
        }
        if ((!config.autoResume && !manualResume) || resumePolicy.exhausted() || !recoveryWaiting || recovery == null || mc.player == null
                || mc.getConnection() == null || !recoveryServerAllowed()) { resumeCountdown = null; return; }
        if (recovery.capture() != null && recovery.capture().config().smartDelay) {
            Config savedSettings = recovery.capture().config();
            readyAfter = Math.max(readyAfter, delayStore.state(savedSettings.smartDelayPerServer, serverKey()).cooldownUntil);
        }
        if (active() || queueActive() || saving || restoring || now() < readyAfter) return;
        if (resumeCountdown == null || resumeCountdown.delay() != config.autoResumeDelayMs) {
            resumeCountdown = new ResumeCountdown(now(), config.autoResumeDelayMs);
            resumeZeroAt = -1;
            playConfiguredSound("minecraft:entity.experience_orb.pickup");
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("[CoreTrace] " + tr("recovery.warning",
                    config.autoResumeDelayMs)).withStyle(net.minecraft.ChatFormatting.RED));
            if (config.resumeAcrossServers) recoveryAddressNotice.schedule(now());
        }
        if (resumeCountdown.soundDue(now())) playConfiguredSound("minecraft:entity.experience_orb.pickup");
        if (resumeCountdown.remaining(now()) == 0) {
            if (resumeZeroAt < 0) resumeZeroAt = now();
            if (now() - resumeZeroAt >= 250) restoreRecovery();
        }
    }
    private void restoreRecovery() {
        var state = recovery;
        if (!resumePolicy.attempt()) return;
        int generation = ++recoveryGeneration;
        resumeCountdown = null; restoring = true;
        io.execute(() -> {
            try {
                var pages = state.capture() == null ? java.util.List.<CaptureEngine.Page>of() : recoveryStore.pages(state.capture());
                QueueExcel workbook = state.excelDirectory() == null ? null : new QueueExcel(exports, state.excelDirectory(), state.excelFileName());
                Minecraft.getInstance().execute(() -> {
                    if (generation != recoveryGeneration) return;
                    restoring = false;
                    if ((!config.autoResume && !manualResume) || !recoveryWaiting || Minecraft.getInstance().getConnection() == null
                            || !recoveryServerAllowed()) return;
                    recoveryWaiting = false;
                    resumeStarting = state.capture(); recoveredPages = pages; queueExcel = workbook;
                    playConfiguredSound("minecraft:entity.villager.yes");
                    try {
                        if (state.queue() != null) {
                            var q = state.queue();
                            createTaskQueue(q.tasks(), q.delay(), state.finishSound(), null);
                            taskQueue.restore(q, now());
                        } else begin(LookupCommand.parse(state.capture().command()).orElseThrow(), true,
                                state.capture().config().captureCopy(), null);
                    } catch (Exception e) {
                        if (queueActive()) taskQueue.cancel();
                        recoveryWaiting = !resumePolicy.exhausted(); readyAfter = now() + 4000;
                        if (resumePolicy.exhausted()) reportOperationFailure("recovery", "restore_failed", e);
                        LOGGER.error("Recovery failed", e); noticeKey("recovery.load_failed");
                    } finally {
                        if (!active() && !queueActive() && recovery != null) { recoveryWaiting = !resumePolicy.exhausted(); readyAfter = Math.max(readyAfter, now() + 4000); }
                        resumeStarting = null; recoveredPages = java.util.List.of();
                    }
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> {
                    if (generation != recoveryGeneration) return;
                    restoring = false; recoveryWaiting = !resumePolicy.exhausted(); readyAfter = now() + 4000;
                    if (resumePolicy.exhausted()) reportOperationFailure("recovery", "pages_load_failed", e);
                    LOGGER.error("Recovery pages could not be loaded", e); noticeKey("recovery.load_failed");
                });
            }
        });
    }
    private static long now() { return System.currentTimeMillis(); }
}
