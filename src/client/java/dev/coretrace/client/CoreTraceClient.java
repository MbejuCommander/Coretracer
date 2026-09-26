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
    private Context context;
    private boolean ownCommand, openPending;
    private String feedbackKey = "status.ready";
    private Object[] feedbackArgs = new Object[0];
    private Path lastDirectory;
    private Path lastExportFile;
    private net.minecraft.client.resources.sounds.SoundInstance preview;
    private KeyMapping key;
    private long readyAfter;
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
        if (!config.lastExportFile.isBlank()) {
            try {
                lastExportFile = Path.of(config.lastExportFile).toAbsolutePath();
                lastDirectory = lastExportFile.getParent();
            } catch (RuntimeException e) { LOGGER.debug("Invalid saved export path", e); }
        }
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.coretrace.menu", InputConstants.KEY_F8,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("coretrace", "main"))));
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            if (!ownCommand && active() && LookupCommand.isCoreProtect(command)) {
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
            if (!ownCommand && config.automatic) LookupCommand.parse(command).filter(LookupCommand::query)
                    .ifPresent(c -> begin(c, false));
        });
        ClientSendMessageEvents.COMMAND_CANCELED.register(command -> {
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
            boolean captured = engine.accept(ComponentReader.read(message, config.captureHovers, now()), now());
            return !captured || !config.hideCapturedChat;
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) openPending = true;
            if (openPending) { openPending = false; client.gui.setScreen(new MainScreen(client.gui.screen(), null)); }
            if (active()) engine.tick(now());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (active()) engine.endKey(CaptureEngine.Outcome.DISCONNECTED, "reason.disconnected", now());
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (active()) engine.endKey(CaptureEngine.Outcome.DISCONNECTED, "reason.shutdown", now());
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
        return begin(parsed.get(), true);
    }
    private boolean begin(LookupCommand command, boolean send) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) { noticeKey("notice.connect"); return false; }
        if (active()) { noticeKey("notice.busy"); return false; }
        if (now() < readyAfter) { noticeKey("notice.cooldown", (readyAfter - now()) / 1000 + 1); return false; }
        long started = now();
        String server = mc.getCurrentServer() == null ? "local" : mc.getCurrentServer().ip;
        Path path = exports.resolve(NAME.format(Instant.ofEpochMilli(started)) + "-" + UUID.randomUUID().toString().substring(0, 8));
        var settings = config.settings();
        Context ctx = new Context(path, settings.language(), config); context = ctx;
        queue(ctx, () -> ctx.files.begin(command.command(), server, started));
        engine = new CaptureEngine(command, settings, new CaptureEngine.Sink() {
            @Override public void send(String text) { sendLookup(text); }
            @Override public void line(int page, MessageData m) { queue(ctx, () -> ctx.files.line(page, m)); }
            @Override public void page(CaptureEngine.Page p) { queue(ctx, () -> ctx.files.page(p)); }
            @Override public void finish(CaptureEngine.Snapshot snapshot) {
                if (snapshot.outcome() != CaptureEngine.Outcome.COMPLETE && snapshot.outcome() != CaptureEngine.Outcome.EMPTY
                        && snapshot.outcome() != CaptureEngine.Outcome.DISCONNECTED)
                    readyAfter = now() + config.timeoutMs;
                setFeedback("status.saving");
                queue(ctx, () -> {
                    ctx.files.finish(snapshot);
                    mc.execute(() -> {
                        lastDirectory = ctx.files.directory();
                        lastExportFile = ctx.files.lastCsvPath();
                        if (lastExportFile != null) {
                            config.lastExportFile = lastExportFile.toString();
                            saveConfig();
                        }
                        playConfiguredSound(config.finishSound);
                        if (context == ctx) setFeedback("notice.finished", snapshot.outcome(), snapshot.pages().size());
                        displayNotice(tr("notice.finished", snapshot.outcome(), snapshot.pages().size()));
                    });
                });
            }
        }, server, started);
        config.remember(command.command()); saveConfig();
        setFeedback("status.started");
        playConfiguredSound(config.startSound);
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
            if (active()) engine.endKey(CaptureEngine.Outcome.DISCONNECTED, "reason.no_connection", now());
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
                        if (active()) engine.endKey(CaptureEngine.Outcome.STORAGE_ERROR, "reason.storage_error", now());
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
    public void cancel() { if (active()) engine.endKey(CaptureEngine.Outcome.CANCELLED, "reason.cancelled", now()); }
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
                            .sorted(java.util.Comparator.<Path, String>comparing(p -> p.getParent().getFileName().toString()).reversed()
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
    public boolean active() { return engine != null && engine.active(); }
    public CaptureEngine engine() { return engine; }
    public Config config() { return config; }
    public String status() {
        if (active()) return engine.detail(language());
        if (now() < readyAfter) return tr("status.cooldown", (readyAfter - now()) / 1000 + 1, feedback());
        return feedback();
    }
    public String lastQuery() { return config.recentQueries.isEmpty() ? "co l a:-block u:jose t:3d" : config.recentQueries.getFirst(); }
    private static long now() { return System.currentTimeMillis(); }
}
