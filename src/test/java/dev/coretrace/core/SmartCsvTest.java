package dev.coretrace.core;

import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SmartCsvTest {
    @TempDir Path temp;
    private static final String QUERY = "co l a:sign t:7d";
    private static MessageData sign(int second, String text) {
        return new MessageData("0.01/h ago - jose: " + text,
                List.of("2026-10-10 12:00:" + String.format(Locale.ROOT, "%02d", second) + " UTC"), List.of(), 1000);
    }
    private static void page(CaptureEngine engine, int number, int total, long now, MessageData... entries) {
        engine.accept(MessageData.plain(CaptureEngineTest.HEADER, now), now);
        for (var entry : entries) engine.accept(entry, now);
        engine.accept(MessageData.plain("◀ Page " + number + "/" + total + " ▶", now), now);
    }
    private static Config smart() { var c = new Config(); c.csvSmart = true; return c; }

    @Test void tooFewDistinctAnchorsPreserveSavedPageAsPartial() {
        var sink = new RecoveryTest.Sink();
        var old = List.of(new CaptureEngine.Page(1, true, List.of(sign(20, "old A"), sign(19, "old B"))));
        var engine = CaptureEngine.resume(LookupCommand.parse(QUERY).orElseThrow(), smart().settings(), sink,
                "example.test", 1000, 1, 2, 2, old);
        assertEquals(2, engine.expectedPage()); assertEquals(1, engine.completedPages());
        page(engine, 1, 3, 1000, sign(24, "new 1"), sign(23, "new 2"), sign(22, "new 3"), sign(21, "new 4"));
        engine.tick(2500); page(engine, 2, 3, 2600, sign(20, "old A"), sign(19, "old B"));
        engine.tick(4100); page(engine, 3, 3, 4200, sign(18, "remaining"));
        assertEquals(CaptureEngine.Outcome.MISMATCH, sink.result.outcome());
        assertEquals(1, engine.completedPages());
    }
    @Test void identicalEventsInTheSameSecondRemainSeparate() {
        var sink = new RecoveryTest.Sink();
        var engine = new CaptureEngine(LookupCommand.parse(QUERY).orElseThrow(), smart().settings(), sink, "server", 1);
        page(engine, 1, 1, 1, sign(20, "same"), sign(20, "same"));
        assertEquals(CaptureEngine.Outcome.COMPLETE, sink.result.outcome());
        assertEquals(2, Report.rows(sink.result).size());
    }
    @Test void missingTimestampStopsWithPartialData() {
        var sink = new RecoveryTest.Sink();
        var engine = new CaptureEngine(LookupCommand.parse(QUERY).orElseThrow(), smart().settings(), sink, "server", 1);
        page(engine, 1, 1, 1, MessageData.plain("0.01/h ago - jose: missing time", 1));
        assertEquals(CaptureEngine.Outcome.MISMATCH, sink.result.outcome());
        assertEquals(1, Report.rows(sink.result).size());
    }
    @Test void shiftedPageKeepsSavedProgressDuringOverlapSearch() {
        var sink = new RecoveryTest.Sink();
        var engine = new CaptureEngine(LookupCommand.parse(QUERY).orElseThrow(), smart().settings(), sink, "server", 1000);
        page(engine, 1, 3, 1000, sign(20, "old A"), sign(19, "old B"));
        engine.tick(2500); page(engine, 2, 3, 2600, sign(21, "shifted record"));
        assertTrue(engine.active()); assertNotNull(engine.inspectionCountdown());
        engine.tick(6600); engine.tick(6850);
        assertEquals(1, engine.completedPages());
        assertEquals(2, engine.expectedPage());
        assertEquals(QUERY, sink.sent.getLast());
        page(engine, 1, 3, 6900, sign(22, "new"), sign(21, "shifted record"));
        assertEquals(1, engine.completedPages());
    }
    @Test void changedResultsKeepConfirmedPageAndStopWhenOverlapIsMissing() {
        var sink = new RecoveryTest.Sink();
        var engine = new CaptureEngine(LookupCommand.parse(QUERY).orElseThrow(), smart().settings(), sink, "server", 1000);
        page(engine, 1, 2, 1000, sign(20, "old"));
        engine.tick(2500); page(engine, 2, 2, 2600, sign(21, "new"));
        engine.tick(6600); engine.tick(6850);
        page(engine, 1, 1, 6900, sign(22, "newer"));
        assertEquals(CaptureEngine.Outcome.MISMATCH, sink.result.outcome());
        assertEquals(1, engine.completedPages());
        assertEquals(1, sink.sent.stream().filter(QUERY::equals).count());
    }
    @Test void shiftRetryBudgetResetsOnlyAfterConfiguredConfirmedPages() throws Exception {
        var config = smart(); config.delayMs = 750; config.smartStablePages = 5; config.smartShiftRetries = 3;
        var attempts = CaptureEngine.class.getDeclaredField("smartShiftAttempts");
        attempts.setAccessible(true);
        for (int stable : List.of(4, 5)) {
            var sink = new RecoveryTest.Sink();
            var engine = new CaptureEngine(LookupCommand.parse(QUERY).orElseThrow(), config.settings(), sink,
                    "server", 1000);
            engine.configureSmartStablePages(config.smartStablePages);
            engine.configureSmartShiftRetries(config.smartShiftRetries);
            attempts.setInt(engine, 3); // Three previous shifts in the same progress window.
            for (int n = 1; n <= stable; n++) {
                long now = 1000L * n;
                if (n > 1) engine.tick(now);
                page(engine, n, 50, now, sign(59 - n, "saved " + n));
            }
            long shiftedAt = 1000L * (stable + 1);
            engine.tick(shiftedAt);
            page(engine, stable + 1, 50, shiftedAt, sign(59, "newer"));
            if (stable == 4) assertEquals(CaptureEngine.Outcome.MISMATCH, sink.result.outcome());
            else { assertTrue(engine.active()); assertNotNull(engine.inspectionCountdown()); }
        }
        assertEquals(20, new Gson().fromJson("{}", Config.class).smartStablePages);
        config.save(temp.resolve("stable-config.json"));
        assertEquals(5, Config.load(temp.resolve("stable-config.json")).smartStablePages);
    }
    @Test void smartShiftRetryLimitDefaultsToFiveAndCanBeChanged() throws Exception {
        assertEquals(5, new Gson().fromJson("{}", Config.class).smartShiftRetries);
        var config = smart(); config.smartShiftRetries = 10;
        config.save(temp.resolve("retries-config.json"));
        assertEquals(10, Config.load(temp.resolve("retries-config.json")).smartShiftRetries);
        config.smartShiftRetries = 0; config.normalize(); assertEquals(5, config.smartShiftRetries);
        config.smartShiftRetries = 11; config.normalize(); assertEquals(5, config.smartShiftRetries);

        var attempts = CaptureEngine.class.getDeclaredField("smartShiftAttempts");
        attempts.setAccessible(true);
        for (int configured : List.of(1, 5, 10)) {
            var sink = new RecoveryTest.Sink();
            var engine = new CaptureEngine(LookupCommand.parse(QUERY).orElseThrow(), smart().settings(), sink, "server", 1000);
            engine.configureSmartShiftRetries(configured);
            attempts.setInt(engine, configured - 1);
            page(engine, 1, 3, 1000, sign(20, "old"));
            engine.tick(2500); page(engine, 2, 3, 2600, sign(21, "shifted"));
            assertTrue(engine.active(), "The final configured retry should be allowed");
            assertEquals(configured, attempts.getInt(engine));
        }
        assertThrows(IllegalArgumentException.class, () -> new CaptureEngine(LookupCommand.parse(QUERY).orElseThrow(), smart().settings(), new RecoveryTest.Sink(), "server", 1).configureSmartShiftRetries(0));
    }
    @Test void timestampValidationStillRejectsInvalidDatesAndChangedZones() {
        var order = new SmartCsvOrder();
        assertNotNull(order.accept("2026-02-30 12:00:00 UTC"));
        assertNull(order.accept("2026-10-10 12:00:00 UTC"));
        assertEquals("smart.changed_zone", order.accept("2026-10-10 11:00:00 GMT"));
    }
    @Test void timestampCannotBeRemovedWhileSmartModeIsEnabledAndClearAllPreservesIt() throws Exception {
        var c = smart(); var draft = new CsvColumnSelection(c.csvColumns);
        draft.toggleAll(); c.csvColumns = new ArrayList<>(draft.selected());
        assertTrue(c.smartCsvEnabled());
        draft.toggle("raw_text"); draft.toggle("server_timestamp");
        c.csvColumns = new ArrayList<>(draft.selected()); c.save(temp.resolve("config.json"));
        var loaded = Config.load(temp.resolve("config.json"));
        assertTrue(loaded.smartCsvEnabled()); assertTrue(loaded.settings().smartCsv());
        assertTrue(new Config().csvSmart);
    }
    @Test void taskCsvOverridesAndCopiesPreserveSmartChoice() {
        var universal = smart(); var task = new TaskDefinition(); task.command = QUERY;
        assertTrue(task.effective(universal).smartCsvEnabled());
        task.csvSettings = new Config(); task.csvSettings.csvSmart = false;
        assertFalse(task.effective(universal).smartCsvEnabled());
        task.csvSettings.csvSmart = true;
        assertTrue(task.copy().effective(new Config()).smartCsvEnabled());
    }
    @Test void smartRecoveryLoadsSavedPagesAndStopsIfOverlapCannotBeVerified() throws Exception {
        Path exports = Files.createDirectories(temp.resolve("exports")); Files.createDirectories(exports.resolve("run"));
        var store = new RecoveryStore(temp.resolve("recovery.json"), exports);
        var c = new RecoveryStore.Capture("run", QUERY, 1, 185, 200, smart());
        for (int number = 1; number < 185; number++)
            store.writePage(c, new CaptureEngine.Page(number, true, List.of(sign(20, "saved " + number))));
        store.save(new RecoveryStore.State("server", c, null, ""));
        assertEquals(184, store.pages(store.load().capture()).size());
        var sink = new RecoveryTest.Sink();
        var engine = CaptureEngine.resume(LookupCommand.parse(QUERY).orElseThrow(), c.config().settings(), sink,
                "server", 1000, 1, 185, 200, store.pages(c));
        page(engine, 1, 1, 1000, sign(20, "only current event"));
        assertEquals(CaptureEngine.Outcome.MISMATCH, sink.result.outcome());
        assertEquals(184, engine.completedPages());
    }
    @Test void cleanupPreservesPendingRecoveryAndOnlyDeletesPageFilesAfterTransition() throws Exception {
        Path exports = Files.createDirectories(temp.resolve("exports"));
        Path dir = Files.createDirectories(exports.resolve("run"));
        var store = new RecoveryStore(temp.resolve("recovery.json"), exports);
        var c = new RecoveryStore.Capture("run", QUERY, 1, 2, 2, new Config());
        store.writePage(c, new CaptureEngine.Page(1, true, List.of(sign(20, "saved"))));
        Files.writeString(dir.resolve(".recovery-page-2.json.tmp"), "incomplete temporary file");
        Files.writeString(dir.resolve("records.csv"), "final csv");
        Files.writeString(dir.resolve("notes.json"), "user data");
        store.save(new RecoveryStore.State("server", c, null, ""));
        assertFalse(store.cleanupCompleted(dir));
        assertTrue(Files.exists(dir.resolve(".recovery-page-1.json")));
        store.save(null); assertTrue(store.cleanupCompleted(dir));
        assertFalse(Files.exists(dir.resolve(".recovery-page-1.json")));
        assertFalse(Files.exists(dir.resolve(".recovery-page-2.json.tmp")));
        assertEquals("final csv", Files.readString(dir.resolve("records.csv")));
        assertEquals("user data", Files.readString(dir.resolve("notes.json")));
        assertThrows(java.io.IOException.class, () -> store.cleanupCompleted(temp));
    }
    @Test void refreshedShorterExportRemovesObsoleteCsvParts() throws Exception {
        var c = smart(); c.csvPagesPerFile = 1;
        var sink = new RecoveryTest.Sink();
        var engine = new CaptureEngine(LookupCommand.parse(QUERY).orElseThrow(), c.settings(), sink, "server", 1);
        page(engine, 1, 2, 1, sign(20, "one")); engine.tick(1501);
        page(engine, 2, 2, 1600, sign(19, "expired later"));
        var dir = temp.resolve("export");
        try (var files = new SessionFiles(dir, Language.SPANISH, c)) { files.begin(QUERY, "server", 1); files.finish(sink.result); }
        assertTrue(Files.exists(dir.resolve("registros_2.csv")));
        var second = new RecoveryTest.Sink();
        var refreshed = new CaptureEngine(LookupCommand.parse(QUERY).orElseThrow(), c.settings(), second, "server", 2000);
        page(refreshed, 1, 1, 2000, sign(20, "one"));
        try (var files = new SessionFiles(dir, Language.SPANISH, c)) { files.resume(1); files.finish(second.result); }
        assertTrue(Files.exists(dir.resolve("registros.csv")));
        assertFalse(Files.exists(dir.resolve("registros_1.csv")));
        assertFalse(Files.exists(dir.resolve("registros_2.csv")));
    }
}
