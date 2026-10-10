package dev.coretrace.core;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryTest {
    @TempDir Path temp;
    static final String HEADER = CaptureEngineTest.HEADER;
    static final String ENTRY = CaptureEngineTest.ENTRY;
    static final class Sink implements CaptureEngine.Sink {
        final List<String> sent = new ArrayList<>();
        final List<MessageData> logged = new ArrayList<>();
        CaptureEngine.Snapshot result;
        public void send(String command) { sent.add(command); }
        public void line(int page, MessageData message) { logged.add(message); }
        public void page(CaptureEngine.Page page) {}
        public void finish(CaptureEngine.Snapshot snapshot) { result = snapshot; }
    }
    private List<CaptureEngine.Page> pages(int count) {
        var pages = new ArrayList<CaptureEngine.Page>();
        for (int i = 1; i <= count; i++) pages.add(new CaptureEngine.Page(i, true, List.of(MessageData.plain(ENTRY, i))));
        return pages;
    }
    private CaptureEngine resume(int page, int total, Sink sink) {
        return CaptureEngine.resume(LookupCommand.parse("co l a:sign t:7d").orElseThrow(),
                new Config().settings(), sink, "example.test", 1000, 1, page, total, pages(page - 1));
    }
    private void body(CaptureEngine engine, long now) {
        engine.accept(MessageData.plain(HEADER, now), now);
        engine.accept(MessageData.plain(ENTRY, now), now);
    }
    private void footer(CaptureEngine engine, int page, int total, long now) {
        engine.accept(MessageData.plain("◀ Page " + page + "/" + total + " ▶", now), now);
    }
    @Test void resumes185WithoutLoggingOrExportingTheRecreatedPageOne() {
        Sink sink = new Sink(); var engine = resume(185, 185, sink);
        body(engine, 1000); footer(engine, 1, 185, 1000);
        assertTrue(sink.logged.isEmpty()); assertEquals(184, engine.completedPages());
        engine.tick(2499); assertTrue(sink.sent.isEmpty());
        engine.tick(2500); assertEquals(List.of("co l 185"), sink.sent);
        body(engine, 2501); footer(engine, 185, 185, 2501);
        assertEquals(CaptureEngine.Outcome.COMPLETE, sink.result.outcome());
        assertEquals(185, Report.rows(sink.result).size());
        assertEquals("co l a:sign t:7d", sink.result.command());
        assertEquals(1, sink.result.startedAt());
        assertEquals(1, sink.result.firstPage());
    }
    @Test void missingBootstrapFooterNeverJumpsOrAcceptsItAsASinglePage() {
        Sink sink = new Sink(); var engine = resume(185, 200, sink);
        body(engine, 1000); engine.tick(5000);
        assertNull(sink.result); assertTrue(sink.sent.isEmpty());
        engine.tick(31000);
        assertEquals(CaptureEngine.Outcome.TIMEOUT, sink.result.outcome());
        assertEquals(184, Report.rows(sink.result).size());
        assertTrue(sink.result.pending().isEmpty());
    }
    @Test void changedLookupTotalStopsBeforeRequestingTheSavedPage() {
        Sink sink = new Sink(); var engine = resume(185, 200, sink);
        body(engine, 1000); footer(engine, 1, 201, 1001); engine.tick(5000);
        assertEquals(CaptureEngine.Outcome.MISMATCH, sink.result.outcome());
        assertTrue(sink.sent.isEmpty()); assertTrue(sink.logged.isEmpty());
    }
    @Test void bootstrapIgnoresUnrelatedChatAndWaitsAfterRepeatedFooter() {
        Sink sink = new Sink(); var engine = resume(2, 3, sink);
        assertFalse(engine.accept(MessageData.plain("<player> hi", 1000), 1000));
        body(engine, 1000); footer(engine, 1, 3, 1000); footer(engine, 1, 3, 2000);
        engine.tick(3499); assertTrue(sink.sent.isEmpty()); engine.tick(3500);
        assertEquals(List.of("co l 2"), sink.sent);
    }
    @Test void interruptedTargetPageIsReplacedRatherThanAppendedOnSecondReconnect() {
        Sink first = new Sink(); var engine = resume(2, 3, first);
        body(engine, 1000); footer(engine, 1, 3, 1000); engine.tick(2500);
        body(engine, 2600); engine.endKey(CaptureEngine.Outcome.DISCONNECTED, "reason.disconnected", 2700);
        assertFalse(first.result.pending().isEmpty());
        Sink second = new Sink(); var recovered = CaptureEngine.resume(LookupCommand.parse(first.result.command()).orElseThrow(),
                new Config().settings(), second, "example.test", 1000, 1, 2, 3, first.result.pages());
        body(recovered, 1000); footer(recovered, 1, 3, 1000); recovered.tick(2500);
        body(recovered, 2600); footer(recovered, 2, 3, 2600); recovered.tick(4100);
        body(recovered, 4200); footer(recovered, 3, 3, 4200);
        assertEquals(3, Report.rows(second.result).size());
    }
    @Test void pageOneRestartCapturesItsResponseExactlyOnce() {
        Sink sink = new Sink(); var engine = resume(1, 0, sink);
        body(engine, 1000); footer(engine, 1, 1, 1000);
        assertEquals(1, Report.rows(sink.result).size()); assertTrue(sink.sent.isEmpty());
    }
    @Test void pagesAndFrozenSettingsSurviveAProcessRestart() throws Exception {
        Path exports = Files.createDirectories(temp.resolve("exports")); Files.createDirectories(exports.resolve("julio"));
        var store = new RecoveryStore(temp.resolve("recovery.json"), exports);
        var config = new Config(); config.csvFileName = "julio"; config.csvExcelAutoColumns = true;
        var capture = new RecoveryStore.Capture("julio", "co l a:sign t:7d", 123, 3, 4, config);
        for (var p : pages(2)) store.writePage(capture, p);
        store.save(new RecoveryStore.State("example.test", capture, null, ""));
        var freshStore = new RecoveryStore(temp.resolve("recovery.json"), exports);
        var restored = freshStore.load();
        assertEquals(3, restored.capture().page()); assertEquals(2, freshStore.pages(restored.capture()).size());
        assertEquals("julio", restored.capture().config().csvFileName);
        assertTrue(restored.capture().config().csvExcelAutoColumns);
        assertEquals("example.test", restored.server());
        Files.delete(exports.resolve("julio/.recovery-page-2.json"));
        assertThrows(java.io.IOException.class, () -> freshStore.pages(restored.capture()));
    }
    @Test void incompleteNewPageCannotInvalidateThePreviousCheckpoint() throws Exception {
        Path exports = Files.createDirectories(temp.resolve("exports")); Files.createDirectories(exports.resolve("run"));
        var store = new RecoveryStore(temp.resolve("recovery.json"), exports);
        var capture = new RecoveryStore.Capture("run", "co l a:sign t:7d", 1, 2, 5, new Config());
        store.writePage(capture, pages(1).getFirst());
        store.save(new RecoveryStore.State("example.test", capture, null, ""));
        Files.writeString(exports.resolve("run/.recovery-page-2.json.tmp"), "partial");
        assertEquals(2, store.load().capture().page()); assertEquals(1, store.pages(capture).size());
        store.save(null); assertNull(store.load());
    }
    @Test void recoveryCannotWriteOutsideExports() throws Exception {
        Path exports = Files.createDirectories(temp.resolve("exports"));
        var store = new RecoveryStore(temp.resolve("recovery.json"), exports);
        var capture = new RecoveryStore.Capture("..", "co l a:sign t:7d", 1, 1, 0, new Config());
        assertThrows(java.io.IOException.class, () -> store.writePage(capture, pages(1).getFirst()));
    }
    @Test void countdownBeepsOncePerElapsedSecondAndSupportsZeroAndLargeDelays() {
        var clock = new ResumeCountdown(100, 4000);
        assertEquals(4, clock.seconds(100)); assertFalse(clock.soundDue(1099));
        for (int second = 1; second <= 4; second++) {
            long now = 100 + second * 1000;
            assertTrue(clock.soundDue(now)); assertFalse(clock.soundDue(now));
            assertEquals(4 - second, clock.seconds(now));
        }
        assertEquals(0, clock.remaining(4100));
        assertEquals(0, new ResumeCountdown(100, 0).remaining(100));
        assertEquals(5, new ResumeCountdown(100, 4500).seconds(100));
        assertTrue(new ResumeCountdown(100, Long.MAX_VALUE).remaining(101) > 0);
        assertEquals(4000, ResumeCountdown.parseDelay(" ")); assertEquals(0, ResumeCountdown.parseDelay("0"));
        assertEquals(9999999999L, ResumeCountdown.parseDelay("9999999999"));
        for (String invalid : List.of("-1", "1.5", "bad", "9223372036854775808"))
            assertThrows(IllegalArgumentException.class, () -> ResumeCountdown.parseDelay(invalid));
    }
    @Test void legacyConfigsDefaultToEnabledAndFourSeconds() throws Exception {
        Path path = temp.resolve("config.json"); Files.writeString(path, "{}");
        var config = Config.load(path); assertTrue(config.autoResume); assertEquals(4000, config.autoResumeDelayMs);
        config.autoResume = false; config.autoResumeDelayMs = 12345678901L; config.save(path);
        var restored = Config.load(path); assertFalse(restored.autoResume); assertEquals(12345678901L, restored.autoResumeDelayMs);
    }
    @Test void queueSettingsAndCommandPositionSurviveJsonReload() throws Exception {
        Path exports = Files.createDirectories(temp.resolve("exports"));
        var store = new RecoveryStore(temp.resolve("recovery.json"), exports);
        var task = new TaskDefinition(); task.command = "/co l a:sign t:7d"; task.csvName = "one";
        task.universalSettings = false; task.taskSettings = new Config(); task.taskSettings.delayMs = 2200;
        task.csvSettings = new Config(); task.csvSettings.csvColumns = new ArrayList<>(List.of("raw_text"));
        task.commands.add(new TaskCommand("/alreadySent", 100)); task.commands.add(new TaskCommand("/pending", 200));
        var queue = new TaskQueue.State(List.of(task), 3000, 0, 1, TaskQueue.Phase.CUSTOM, 123);
        store.save(new RecoveryStore.State("example.test", null, queue, "minecraft:ui.toast.in"));
        var restored = store.load();
        assertEquals(1, restored.queue().commandIndex()); assertEquals(123, restored.queue().remainingMs());
        var t = restored.queue().tasks().getFirst();
        assertEquals(2200, t.effective(new Config()).delayMs);
        assertEquals(List.of("raw_text"), t.effective(new Config()).csvColumns);
        assertEquals("/pending", t.commands.get(1).command);
        task.csvSettings.csvColumns.clear(); assertEquals(List.of("raw_text"), t.csvSettings.csvColumns);
    }
    @Test void resumedExportReusesTheFolderAndWritesEachPageOnlyOnce() throws Exception {
        var config = new Config(); config.csvFileName = "julio"; config.csvPagesPerFile = 1;
        Path directory = temp.resolve("julio");
        var partial = new CaptureEngine.Snapshot("co l a:sign t:7d", "test", 1, 10, 1, 2,
                CaptureEngine.Outcome.DISCONNECTED, "test", pages(1), List.of(MessageData.plain(ENTRY, 10)), List.of(), "es_es");
        try (var files = new SessionFiles(directory, Language.SPANISH, config)) {
            files.begin(partial.command(), partial.server(), partial.startedAt()); files.finish(partial);
        }
        var complete = new CaptureEngine.Snapshot(partial.command(), "test", 1, 20, 1, 2,
                CaptureEngine.Outcome.COMPLETE, "test", pages(2), List.of(), List.of(), "es_es");
        try (var files = new SessionFiles(directory, Language.SPANISH, config)) { files.resume(2); files.finish(complete); }
        assertEquals(2, Files.readString(directory.resolve("julio_1.csv")).lines().count());
        assertEquals(2, Files.readString(directory.resolve("julio_2.csv")).lines().count());
        assertTrue(Files.readString(directory.resolve("transcript.log")).contains("REANUDACIÓN"));
    }
}
