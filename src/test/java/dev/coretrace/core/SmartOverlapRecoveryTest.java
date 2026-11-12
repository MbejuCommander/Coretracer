package dev.coretrace.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SmartOverlapRecoveryTest {
    @TempDir Path temp;
    private static final String QUERY = "co l a:sign t:7d";
    @Test void sessionRecoveryIgnoresChangingCaretAlignmentButChecksRealFields() {
        var old = new ArrayList<MessageData>();
        var fresh = new ArrayList<MessageData>();
        old.add(MessageData.plain(CaptureEngineTest.HEADER, 1000));
        fresh.add(MessageData.plain(CaptureEngineTest.HEADER, 2000));
        String[] stamps = {"2026-11-11 05:51:17 UTC", "2026-11-11 05:50:10 UTC",
                "2026-11-11 05:01:18 UTC", "2026-11-11 05:01:14 UTC"};
        for (int i = 0; i < stamps.length; i++) {
            String action = i % 2 == 0 ? "logged out." : "logged in.";
            String sign = i % 2 == 0 ? "-" : "+";
            old.add(new MessageData((i == 0 ? "9.98/h" : "10.81/h") + " ago " + sign + " example_player " + action,
                    List.of(stamps[i]), List.of(), 1000));
            fresh.add(new MessageData((i == 0 ? "10.04/h" : "10.87/h") + " ago " + sign + " example_player " + action,
                    List.of(stamps[i]), List.of(), 2000));
            old.add(MessageData.plain(" ".repeat(i == 0 ? 17 : 16) + "^ (x-30807/y-15/z-20924/world)", 1000));
            fresh.add(MessageData.plain(" ".repeat(16) + "^ (x-30807/y-15/z-20924/world)", 2000));
        }
        old.add(MessageData.plain("Page 1/741", 1000));
        fresh.add(MessageData.plain("Page 1/741", 2000));
        var query = EventParser.query("co l u:example_player a:session");
        var expected = SmartOverlap.events(old, query);
        assertEquals(0, SmartOverlap.find(SmartOverlap.events(fresh, query), expected));
        var changedCoordinate = new ArrayList<>(fresh);
        changedCoordinate.set(2, MessageData.plain("                ^ (x-30808/y-15/z-20924/world)", 2000));
        assertEquals(-1, SmartOverlap.find(SmartOverlap.events(changedCoordinate, query), expected));
        var changedTimestamp = new ArrayList<>(fresh);
        changedTimestamp.set(1, new MessageData(fresh.get(1).text(), List.of("2026-11-11 05:51:18 UTC"), List.of(), 2000));
        assertEquals(-1, SmartOverlap.find(SmartOverlap.events(changedTimestamp, query), expected));

        var sink = new RecoveryTest.Sink();
        var savedPages = List.of(new CaptureEngine.Page(1, true, old),
                new CaptureEngine.Page(2, true, List.of(event(1, "10.90/h ago"))));
        var engine = CaptureEngine.resume(LookupCommand.parse("co l u:example_player a:session").orElseThrow(),
                settings(), sink, "server", 2000, 1000, 3, 741, savedPages);
        for (MessageData message : fresh) engine.accept(message, 2000);
        assertTrue(engine.active());
        assertNull(sink.result, "The recovered first page must not be rejected as a changed result");
    }
    private static CaptureEngine.Settings settings() {
        var config = new Config(); config.delayMs = 750; return config.settings();
    }
    private static MessageData event(int second, String relative) {
        return new MessageData(relative + " - jose: event " + second,
                List.of("2026-10-10 12:00:" + String.format(Locale.ROOT, "%02d", second) + " UTC"), List.of(), 1000);
    }
    private static CaptureEngine.Page saved(int page, int... seconds) {
        var messages = new ArrayList<MessageData>();
        messages.add(MessageData.plain(CaptureEngineTest.HEADER, 1000));
        for (int second : seconds) messages.add(event(second, "0.01/h ago"));
        messages.add(MessageData.plain("Page " + page + "/3", 1000));
        return new CaptureEngine.Page(page, true, messages);
    }
    private static void receive(CaptureEngine engine, int page, int total, long time, int... seconds) {
        engine.accept(MessageData.plain(CaptureEngineTest.HEADER, time), time);
        for (int second : seconds) engine.accept(event(second, "0.05/h ago"), time);
        engine.accept(MessageData.plain("Page " + page + "/" + total, time), time);
    }
    private static MessageData numbered(int number, boolean recovered) {
        String stamp = LocalDateTime.of(2026, 10, 10, 12, 0).minusSeconds(number)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return new MessageData((recovered ? "5/h ago" : "1/h ago") + " - jose: item " + number,
                List.of(stamp + " UTC"), List.of(), 1000);
    }
    private static CaptureEngine.Page numberedPage(int page) {
        return new CaptureEngine.Page(page, true, List.of(numbered(3 * (page - 1), false),
                numbered(3 * (page - 1) + 1, false), numbered(3 * (page - 1) + 2, false)));
    }
    private static void numberedResponse(CaptureEngine engine, int page, long now, int... numbers) {
        engine.accept(MessageData.plain(CaptureEngineTest.HEADER, now), now);
        for (int n : numbers) engine.accept(numbered(n, true), now);
        engine.accept(MessageData.plain("Page " + page + "/324", now), now);
    }

    @Test void matchingPrefixDoesNotChangeSavedPagesWhenTailBelongsToDifferentResults() {
        var original = List.of(saved(1, 20, 19, 18), saved(2, 17, 16, 15), saved(3, 14, 13, 12));
        var sink = new RecoveryTest.Sink();
        var engine = CaptureEngine.resume(LookupCommand.parse(QUERY).orElseThrow(), settings(), sink,
                "another-address", 1000, 100, 4, 4, original);
        receive(engine, 1, 4, 1000, 21, 20, 19);
        engine.tick(1750); receive(engine, 2, 4, 1750, 18, 17, 16);
        engine.tick(2500); receive(engine, 2, 3, 2500, 10, 9, 8);
        engine.tick(3250); receive(engine, 3, 3, 3250, 7, 6, 5);
        assertEquals(CaptureEngine.Outcome.MISMATCH, sink.result.outcome());
        assertEquals(original, sink.result.pages());
    }

    @Test void resumesNearPage322AfterTwoNewFrontEntries() {
        var savedPages = new ArrayList<CaptureEngine.Page>();
        for (int page = 1; page <= 322; page++) savedPages.add(numberedPage(page));
        var sink = new RecoveryTest.Sink();
        var engine = CaptureEngine.resume(LookupCommand.parse(QUERY).orElseThrow(), settings(), sink,
                "server", 1000, 100, 323, 323, savedPages);
        numberedResponse(engine, 1, 1000, -2, -1, 0);
        engine.tick(1750); numberedResponse(engine, 2, 1750, 1, 2, 3);
        engine.tick(2500); assertEquals("co l 321", sink.sent.getLast());
        numberedResponse(engine, 321, 2500, 958, 959, 960);
        engine.tick(3250); numberedResponse(engine, 322, 3250, 961, 962, 963);
        engine.tick(4000); numberedResponse(engine, 323, 4000, 964, 965, 966);
        engine.tick(4750); assertEquals("co l 324", sink.sent.getLast());
        numberedResponse(engine, 324, 4750, 967, 968, 969);
        assertEquals(CaptureEngine.Outcome.COMPLETE, sink.result.outcome());
        var rows = Report.rows(sink.result);
        assertEquals(972, rows.size());
        assertEquals("item -2", rows.getFirst().entry().content());
        assertEquals("item 969", rows.getLast().entry().content());
        assertFalse(sink.sent.contains("co l 100"));
    }

    @Test void shiftedResultsDuringCaptureRejoinInsteadOfRestartingSavedPages() {
        var sink = new RecoveryTest.Sink();
        var engine = new CaptureEngine(LookupCommand.parse(QUERY).orElseThrow(), settings(), sink, "server", 1000);
        engine.configureInspectionRecovery(true, 4000);
        receive(engine, 1, 4, 1000, 9, 8, 7);
        engine.tick(1750); receive(engine, 2, 4, 1750, 6, 5, 4);
        engine.tick(2500);
        engine.accept(MessageData.plain(CaptureEngineTest.HEADER, 2500), 2500);
        engine.accept(event(10, "0.05/h ago"), 2500);
        assertNotNull(engine.inspectionCountdown());
        engine.tick(6500); engine.tick(6750);
        assertEquals(2, engine.completedPages());
        assertEquals(QUERY, sink.sent.getLast());
        receive(engine, 1, 4, 6800, 11, 10, 9);
        engine.tick(7550); receive(engine, 2, 4, 7550, 8, 7, 6);
        engine.tick(8300); receive(engine, 1, 4, 8300, 11, 10, 9);
        engine.tick(9050); receive(engine, 2, 4, 9050, 8, 7, 6);
        engine.tick(9800); receive(engine, 3, 4, 9800, 5, 4, 3);
        engine.tick(10550); receive(engine, 4, 4, 10550, 2, 1, 0);
        assertEquals(CaptureEngine.Outcome.COMPLETE, sink.result.outcome());
        assertEquals(12, Report.rows(sink.result).size());
    }

    @Test void rejoiningDoesNotDuplicateOldRowsButKeepsTwoLegitimateIdenticalEvents() {
        var sink = new RecoveryTest.Sink();
        var engine = CaptureEngine.resume(LookupCommand.parse(QUERY).orElseThrow(), settings(), sink,
                "server", 1000, 100, 3, 3, List.of(saved(1, 20, 19, 18), saved(2, 17, 17, 16)));
        receive(engine, 1, 3, 1000, 21, 20, 19);
        engine.tick(1750); receive(engine, 2, 3, 1750, 18, 17, 17);
        engine.tick(2500); receive(engine, 1, 3, 2500, 21, 20, 19);
        engine.tick(3250); receive(engine, 2, 3, 3250, 18, 17, 17);
        engine.tick(4000); receive(engine, 3, 3, 4000, 16, 15);
        assertEquals(CaptureEngine.Outcome.COMPLETE, sink.result.outcome());
        assertEquals(8, Report.rows(sink.result).size());
        assertEquals(2, Report.rows(sink.result).stream().filter(r -> r.entry().content().equals("event 17")).count());
    }

    @Test void newFrontEntriesAndShiftedTailKeepSavedPagesWithoutDuplicates() {
        var sink = new RecoveryTest.Sink();
        var engine = CaptureEngine.resume(LookupCommand.parse(QUERY).orElseThrow(), settings(), sink,
                "server", 1000, 100, 3, 3, List.of(saved(1, 9, 8, 7), saved(2, 6, 5, 4)));
        receive(engine, 1, 4, 1000, 11, 10, 9);
        engine.tick(1750); assertEquals("co l 2", sink.sent.getLast());
        receive(engine, 2, 4, 1750, 8, 7, 6);
        assertEquals(2, engine.completedPages());
        engine.tick(2500); assertEquals("co l 1", sink.sent.getLast());
        receive(engine, 1, 4, 2500, 11, 10, 9);
        engine.tick(3250); receive(engine, 2, 4, 3250, 8, 7, 6);
        engine.tick(4000); receive(engine, 3, 4, 4000, 5, 4, 3);
        assertEquals(3, engine.completedPages());
        engine.tick(4750); assertEquals("co l 4", sink.sent.getLast());
        receive(engine, 4, 4, 4750, 2, 1, 0);
        assertEquals(CaptureEngine.Outcome.COMPLETE, sink.result.outcome());
        assertEquals(List.of(11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0), Report.rows(sink.result).stream()
                .map(r -> Integer.parseInt(r.entry().content().replace("event ", ""))).toList());
        assertEquals(1, sink.sent.stream().filter("co l 3"::equals).count());
    }

    @Test void missingOverlapPreservesAllSavedPagesAsPartial() {
        var sink = new RecoveryTest.Sink();
        var engine = CaptureEngine.resume(LookupCommand.parse(QUERY).orElseThrow(), settings(), sink,
                "server", 1000, 100, 3, 3, List.of(saved(1, 6, 5, 4), saved(2, 3, 2, 1)));
        receive(engine, 1, 1, 1000, 9, 8, 7);
        assertEquals(CaptureEngine.Outcome.MISMATCH, sink.result.outcome());
        assertEquals(2, engine.completedPages());
        assertEquals(6, Report.rows(sink.result).size());
    }

    @Test void replacingFirstDurablePageRebuildsCsvWithoutDuplicatingOldRows() throws Exception {
        var config = new Config(); config.csvExcelAutoColumns = false;
        Path dir = temp.resolve("run");
        var first = saved(1, 9, 8, 7);
        var second = saved(2, 6, 5, 4);
        var merged = new ArrayList<MessageData>();
        merged.add(first.messages().getFirst());
        merged.add(event(11, "0.05/h ago")); merged.add(event(10, "0.05/h ago"));
        merged.addAll(first.messages().subList(1, first.messages().size()));
        var replacement = new CaptureEngine.Page(1, true, merged);
        try (var files = new SessionFiles(dir, Language.SPANISH, config)) {
            files.begin(QUERY, "server", 1000);
            files.page(first); files.page(second);
            files.replaceFirst(replacement, QUERY, 3, List.of(merged.get(1), merged.get(2)));
            files.finish(new CaptureEngine.Snapshot(QUERY, "server", 1000, 2000, 1, 2,
                    CaptureEngine.Outcome.COMPLETE, "done", List.of(replacement, second), List.of(), List.of(), "es_es"));
        }
        String csv = Files.readString(dir.resolve("registros.csv"));
        assertEquals(1, csv.lines().filter(line -> line.contains("event 11")).count());
        assertEquals(1, csv.lines().filter(line -> line.contains("event 9")).count());
        assertEquals(1, csv.lines().filter(line -> line.contains("event 4")).count());
        assertTrue(Files.readString(dir.resolve("transcript.log")).contains("event 11"));
        assertEquals(replacement, PageHistory.read(dir, 1));
    }
}
