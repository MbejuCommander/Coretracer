package dev.coretrace.core;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProgressiveCaptureTest {
    @TempDir Path directory;
    static final String QUERY = "co l a:sign t:7d";
    static Config normal() { var c = new Config(); c.csvSmart = false; return c; }
    static CaptureEngine.Page page(int n) {
        return new CaptureEngine.Page(n, true, List.of(MessageData.plain("1/h ago - jose: event " + n, n)));
    }
    static CaptureEngine.Snapshot snapshot(List<CaptureEngine.Page> pages, List<MessageData> pending, CaptureEngine.Outcome outcome) {
        return new CaptureEngine.Snapshot(QUERY, "server", 1, 2, 1, 6000, outcome, "test", pages, pending, List.of(), "en_us");
    }
    @Test void completedPartsExistBeforeFinishAndPartialPartIsNotPublished() throws Exception {
        var config = normal(); config.csvPagesPerFile = 2;
        try (var files = new SessionFiles(directory, Language.ENGLISH, config)) {
            files.begin(QUERY, "server", 1);
            files.page(page(1));
            assertFalse(Files.exists(directory.resolve("records_1.csv")));
            files.page(page(2));
            assertTrue(Files.exists(directory.resolve("records_1.csv")));
            files.page(page(3));
            assertFalse(Files.exists(directory.resolve("records_2.csv")));
            files.finish(snapshot(List.of(page(1), page(2), page(3)), List.of(), CaptureEngine.Outcome.COMPLETE));
            assertTrue(Files.readString(directory.resolve("records_2.csv")).contains("event 3"));
            assertEquals(3, Files.readString(directory.resolve("records_1.csv")).lines().count());
        }
    }
    @Test void crashRecoveryReplacesIncompletePartWithoutDuplicatingPreviousRows() throws Exception {
        var config = normal(); config.csvPagesPerFile = 2;
        try (var files = new SessionFiles(directory, Language.ENGLISH, config)) {
            files.begin(QUERY, "server", 1);
            for (int n = 1; n <= 3; n++) files.page(page(n));
            // Simulated process loss: close writers without final export.
        }
        var saved = PageHistory.load(directory, 1, 3);
        assertEquals(3, saved.size());
        try (var files = new SessionFiles(directory, Language.ENGLISH, config)) {
            files.resume(4); files.restart(QUERY, 1, 4);
            var history = files.history(1); history.restore(saved);
            history.add(page(4)); files.page(page(4));
            files.finish(snapshot(history, List.of(), CaptureEngine.Outcome.COMPLETE));
        }
        for (int part = 1; part <= 2; part++)
            assertEquals(3, Files.readString(directory.resolve("records_" + part + ".csv")).lines().count());
    }
    @Test void streamsOverOneHundredThousandMessagesAndFiveThousandPages() throws Exception {
        var config = normal(); config.language = "en_us"; config.maxPages = 6000; config.csvPagesPerFile = 500;
        config.csvColumns = new ArrayList<>(List.of("page_requested", "raw_text"));
        int pages = 5001, entries = 21;
        try (var files = new SessionFiles(directory, Language.ENGLISH, config)) {
            files.begin(QUERY, "server", 1);
            CaptureEngine.Sink sink = new CaptureEngine.Sink() {
                public List<CaptureEngine.Page> history(int first) { return files.history(first); }
                public void send(String command) {}
                public void line(int page, MessageData message) {}
                public void page(CaptureEngine.Page p) {
                    try { files.page(p); } catch (Exception e) { throw new RuntimeException(e); }
                }
                public void finish(CaptureEngine.Snapshot s) {
                    try { files.finish(s); } catch (Exception e) { throw new RuntimeException(e); }
                }
            };
            var engine = new CaptureEngine(LookupCommand.parse(QUERY).orElseThrow(), config.settings(), sink, "server", 1);
            for (int n = 1; n <= pages; n++) {
                long now = (long)n * 2000;
                engine.accept(MessageData.plain(CaptureEngineTest.HEADER, now), now);
                for (int k = 0; k < entries; k++) engine.accept(MessageData.plain("1/h ago - jose: event " + n + "-" + k, now), now);
                engine.accept(MessageData.plain("Page " + n + "/" + pages, now), now);
                engine.tick(now + 1500);
            }
            assertEquals(CaptureEngine.Outcome.COMPLETE, engine.result().outcome());
            assertEquals(pages, engine.completedPages());
            assertTrue(engine.visibleMessages().size() <= PageHistory.PREVIEW_PAGES * (entries + 2));
            long rows = 0;
            for (int part = 1; part <= 11; part++) {
                try (var lines = Files.lines(directory.resolve("records_" + part + ".csv"))) { rows += lines.count() - 1; }
            }
            assertEquals((long) pages * entries, rows);
            assertTrue(Files.readString(directory.resolve("summary.txt")).contains("105021"));
            assertEquals(pages, PageHistory.load(directory, 1, pages).size());
        }
    }
    @Test void smartDefaultsRequireTimestampButExplicitOptOutPersists() throws Exception {
        var config = new Config(); assertTrue(config.csvSmart); assertTrue(config.inspectRecovery);
        config.csvColumns = new ArrayList<>(List.of("raw_text")); config.normalize();
        assertTrue(config.csvColumns.contains("server_timestamp"));
        config.csvSmart = false; config.csvColumns.remove("server_timestamp");
        config.maxPages = 12000; config.save(directory.resolve("config.json"));
        var loaded = Config.load(directory.resolve("config.json"));
        assertFalse(loaded.csvSmart); assertFalse(loaded.csvColumns.contains("server_timestamp"));
        assertEquals(12000, loaded.maxPages);
    }
    @Test void cancelledExportKeepsRecoveryUntilAllOutputsAreSaved() throws Exception {
        Path exports = Files.createDirectory(directory.resolve("exports"));
        Path run = exports.resolve("run");
        var config = normal();
        var capture = new RecoveryStore.Capture("run", QUERY, 1, 1, 2, config);
        var store = new RecoveryStore(directory.resolve("recovery.json"), exports);
        try (var files = new SessionFiles(run, Language.SPANISH, config)) {
            files.begin(QUERY, "server", 1);
            files.page(page(1));
            store.save(new RecoveryStore.State("server", capture, null, ""));
            assertFalse(store.cleanupCompleted(run));
            files.finish(new CaptureEngine.Snapshot(QUERY, "server", 1, 2, 1, 2,
                    CaptureEngine.Outcome.CANCELLED, "cancelled", List.of(page(1)), List.of(), List.of(), "es_es"));
            assertTrue(Files.isRegularFile(run.resolve("registros.csv")));
            assertTrue(Files.isRegularFile(run.resolve("sesion.json")));
            assertTrue(Files.isRegularFile(run.resolve("resumen.txt")));
            assertFalse(store.cleanupCompleted(run));
            store.save(null);
            assertTrue(store.cleanupCompleted(run));
            assertFalse(Files.exists(run.resolve(".recovery-page-1.json")));
            assertTrue(Files.isRegularFile(run.resolve("registros.csv")));
        }
    }
    @Test void failedCancelledExportLeavesTheRecoveryPointerAndPagesIntact() throws Exception {
        Path exports = Files.createDirectory(directory.resolve("exports"));
        Path run = exports.resolve("run");
        var config = normal();
        var capture = new RecoveryStore.Capture("run", QUERY, 1, 1, 2, config);
        var store = new RecoveryStore(directory.resolve("recovery.json"), exports);
        try (var files = new SessionFiles(run, Language.SPANISH, config)) {
            files.begin(QUERY, "server", 1);
            files.page(page(1));
            store.save(new RecoveryStore.State("server", capture, null, ""));
            // A directory at the summary path forces the final export to fail after the CSV was written.
            Files.createDirectory(run.resolve("resumen.txt"));
            assertThrows(java.io.IOException.class, () -> files.finish(new CaptureEngine.Snapshot(
                    QUERY, "server", 1, 2, 1, 2, CaptureEngine.Outcome.CANCELLED, "cancelled",
                    List.of(page(1)), List.of(), List.of(), "es_es")));
            assertNotNull(store.load());
            assertTrue(Files.isRegularFile(run.resolve(".recovery-page-1.json")));
            assertFalse(store.cleanupCompleted(run));
        }
    }
}
