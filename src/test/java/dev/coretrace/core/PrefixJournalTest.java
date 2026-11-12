package dev.coretrace.core;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PrefixJournalTest {
    @TempDir Path temp;
    private static final String QUERY = "co l a:sign t:7d";
    private static MessageData event(int n) {
        String stamp = LocalDateTime.of(2026, 11, 9, 20, 8).minusSeconds(n)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return new MessageData("1/h ago - jose: event " + n, List.of(stamp + " UTC"), List.of(), 1000);
    }
    private static void receive(CaptureEngine engine, int page, int total, long now, int first) {
        engine.accept(MessageData.plain(CaptureEngineTest.HEADER, now), now);
        for (int n = first; n < first + 3; n++) engine.accept(event(n), now);
        engine.accept(MessageData.plain("Page " + page + "/" + total, now), now);
    }
    @Test void longPrefixIsStagedOnDiskAndRejoinsWithoutReplayingSavedMiddle() throws Exception {
        Path dir = Files.createDirectory(temp.resolve("capture"));
        var config = new Config(); config.delayMs = 750;
        var files = new SessionFiles(dir, Language.SPANISH, config);
        files.begin(QUERY, "original", 100);
        var saved = new ArrayList<CaptureEngine.Page>();
        for (int page = 1; page <= 3; page++) {
            int first = (page - 1) * 3;
            var p = new CaptureEngine.Page(page, true, List.of(event(first), event(first + 1), event(first + 2)));
            saved.add(p); files.page(p);
        }
        var result = new CaptureEngine.Snapshot[1];
        var sent = new ArrayList<String>();
        CaptureEngine.Sink sink = new CaptureEngine.Sink() {
            public void send(String command) { sent.add(command); }
            public void line(int page, MessageData message) {}
            public void page(CaptureEngine.Page page) { try { files.page(page); } catch (Exception e) { throw new RuntimeException(e); } }
            public void finish(CaptureEngine.Snapshot snapshot) {
                result[0] = snapshot;
                try { files.finish(snapshot); } catch (Exception e) { throw new RuntimeException(e); }
            }
            public List<MessageData> prefixBuffer() {
                return new PrefixJournal.Buffer(dir, write -> { try { write.run(); } catch (Exception e) { throw new RuntimeException(e); } });
            }
            public void replaceFirst(CaptureEngine.Page page, int next, List<MessageData> entries, int offset) {
                try { files.replaceFirst(page, QUERY, next, entries, offset); } catch (Exception e) { throw new RuntimeException(e); }
            }
        };
        var engine = CaptureEngine.resume(LookupCommand.parse(QUERY).orElseThrow(), config.settings(), sink,
                "another-address", 1000, 100, 4, 4, saved);
        long now = 1000;
        for (int page = 1; page <= 200; page++) {
            if (page > 1) { now += 750; engine.tick(now); }
            receive(engine, page, 204, now, -600 + (page - 1) * 3);
            assertTrue(engine.active());
            assertFalse(Files.exists(dir.resolve(".recovery-prefix.json")));
        }
        now += 750; engine.tick(now); receive(engine, 201, 204, now, 0);
        now += 750; engine.tick(now); assertEquals("co l 202", sent.getLast());
        receive(engine, 202, 204, now, 3);
        now += 750; engine.tick(now); receive(engine, 203, 204, now, 6);
        now += 750; engine.tick(now); receive(engine, 204, 204, now, 9);
        assertEquals(CaptureEngine.Outcome.COMPLETE, result[0].outcome());
        assertEquals(612, Report.rows(result[0]).size());
        var loaded = PageHistory.load(dir, 1, 4);
        assertTrue(loaded.getFirst().messages() instanceof PrefixJournal.Messages);
        assertEquals(603, loaded.getFirst().messages().size());
        assertTrue(Files.size(dir.resolve(".recovery-page-1.json")) < 3000);
        assertEquals(200, PrefixJournal.savedOffset(dir, 0));
        var store = new RecoveryStore(temp.resolve("recovery.json"), temp);
        store.save(new RecoveryStore.State("old-address", new RecoveryStore.Capture("capture", QUERY, 100, 4, 4, config), null, ""));
        assertEquals(200, store.load().capture().pageOffset(), "Committed manifest repairs a stale pre-commit pointer after a crash");
        store.save(null);
        try (var lines = Files.lines(files.lastCsvPath())) { assertEquals(613, lines.count()); }
        assertTrue(new RecoveryStore(temp.resolve("recovery.json"), temp).cleanupCompleted(dir));
        try (var remaining = Files.list(dir)) { assertFalse(remaining.anyMatch(p -> p.getFileName().toString().startsWith(".recovery-"))); }
    }
    @Test void unverifiedChunksDoNotReplaceCommittedPrefix() throws Exception {
        Path dir = Files.createDirectory(temp.resolve("capture"));
        var first = new PrefixJournal.Buffer(dir, write -> { try { write.run(); } catch (Exception e) { throw new RuntimeException(e); } });
        first.addAll(List.of(event(-1)));
        first.view(List.of(event(0))).publish();
        var second = new PrefixJournal.Buffer(dir, write -> { try { write.run(); } catch (Exception e) { throw new RuntimeException(e); } });
        second.addAll(List.of(event(-2)));
        PrefixJournal.cleanupUnpublished(dir);
        assertEquals(List.of(event(-1), event(0)), PrefixJournal.load(dir, List.of(event(0))));
        try (var files = Files.list(dir)) { assertEquals(2, files.count()); }
        var third = new PrefixJournal.Buffer(dir, write -> { try { write.run(); } catch (Exception e) { throw new RuntimeException(e); } });
        third.addAll(List.of(event(-2)));
        third.view(PrefixJournal.load(dir, List.of(event(0)))).publish(2);
        assertEquals(List.of(event(-2), event(-1), event(0)), PrefixJournal.load(dir, List.of(event(0))));
    }
    @Test void scansEveryEventAtTheTargetSecondBeforeDeclaringTheAnchorMissing() {
        var saved = List.of(new CaptureEngine.Page(1, true, List.of(event(0), event(1), event(2))));
        var sink = new RecoveryTest.Sink();
        var config = new Config(); config.delayMs = 750;
        var engine = CaptureEngine.resume(LookupCommand.parse(QUERY).orElseThrow(), config.settings(), sink,
                "other", 1000, 100, 2, 100, saved);
        long now = 1000;
        for (int page = 1; page <= 20; page++) {
            if (page > 1) { now += 750; engine.tick(now); }
            engine.accept(MessageData.plain(CaptureEngineTest.HEADER, now), now);
            for (int n = 0; n < 3; n++) engine.accept(new MessageData("1/h ago - jose: different " + page + "-" + n,
                    event(0).hovers(), List.of(), now), now);
            engine.accept(MessageData.plain("Page " + page + "/100", now), now);
            assertTrue(engine.active(), "Equal timestamps alone must neither match nor end the search");
        }
        now += 750; engine.tick(now); receive(engine, 21, 100, now, 3);
        assertEquals(CaptureEngine.Outcome.MISMATCH, sink.result.outcome());
        assertEquals(saved, sink.result.pages());
    }
    @Test void olderTimestampWithoutMatchingEventStopsSearchAndPreservesPages() {
        var saved = List.of(new CaptureEngine.Page(1, true, List.of(event(0), event(1), event(2))));
        var sink = new RecoveryTest.Sink();
        var engine = CaptureEngine.resume(LookupCommand.parse(QUERY).orElseThrow(), new Config().settings(), sink,
                "other", 1000, 100, 2, 500, saved);
        receive(engine, 1, 500, 1000, 3);
        assertEquals(CaptureEngine.Outcome.MISMATCH, sink.result.outcome());
        assertEquals(saved, sink.result.pages());
        assertTrue(sink.sent.isEmpty());
    }
}
