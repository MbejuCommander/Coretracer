package dev.coretrace.core;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InspectionRecoveryTest {
    private CaptureEngineTest.Harness harness() {
        var h = new CaptureEngineTest.Harness(); h.engine.configureInspectionRecovery(true, 4000); return h;
    }
    @Test void interruptedPageIsDiscardedAndBootstrapPageOneNeverEntersExport() {
        var h = harness(); h.body(); h.footer(1, 3); h.after(1500); h.body();
        h.engine.blockInteraction(h.now); h.receive(CaptureEngineTest.HEADER);
        assertNotNull(h.engine.inspectionCountdown());
        h.receive("1/h ago - jose: UNRELATED INSPECTION"); h.footer(1, 1);
        h.after(3999); assertEquals(List.of("co l 2"), h.sent);
        h.after(1); assertEquals(0, h.engine.inspectionCountdown().seconds(h.now));
        h.after(250); assertEquals("co l a:-block u:jose t:3d", h.sent.getLast());
        h.body(); h.footer(1, 3); h.after(1500);
        assertEquals("co l 2", h.sent.getLast());
        h.body(); h.footer(2, 3); h.after(1500); h.body(); h.footer(3, 3);
        assertEquals(CaptureEngine.Outcome.COMPLETE, h.finalResult.outcome());
        assertEquals(3, Report.rows(h.finalResult).size());
        assertFalse(Report.csv(h.finalResult).contains("UNRELATED"));
    }
    @Test void completedPageResumesAtNextPageAndLateResponsesExtendQuietWait() {
        var h = harness(); h.body(); h.footer(1, 3);
        h.engine.blockInteraction(h.now); h.receive(CaptureEngineTest.HEADER);
        assertEquals(2, h.engine.expectedPage());
        h.after(4000); h.receive(CaptureEngineTest.ENTRY); h.after(250);
        assertTrue(h.sent.isEmpty()); h.after(3250);
        assertEquals(1, h.sent.size());
    }
    @Test void repeatedInterferenceStopsAfterThreeRestarts() {
        var h = harness();
        for (int n = 0; n < 3; n++) {
            h.engine.blockInteraction(h.now); h.receive(CaptureEngineTest.HEADER);
            h.after(4000); h.after(250); assertTrue(h.engine.active());
        }
        h.engine.blockInteraction(h.now); h.receive(CaptureEngineTest.HEADER);
        assertEquals(CaptureEngine.Outcome.MISMATCH, h.finalResult.outcome());
        assertEquals(3, h.sent.size());
    }
    @Test void cancellationAndDisconnectDuringCountdownCannotSendCommands() {
        for (var outcome : List.of(CaptureEngine.Outcome.CANCELLED, CaptureEngine.Outcome.DISCONNECTED)) {
            var h = harness(); h.body(); h.footer(1, 3);
            h.engine.blockInteraction(h.now); h.receive(CaptureEngineTest.HEADER);
            h.engine.end(outcome, "test", h.now); h.after(10000);
            assertTrue(h.sent.isEmpty()); assertNull(h.engine.inspectionCountdown());
            assertEquals(1, h.finalResult.pages().size()); assertTrue(h.finalResult.pending().isEmpty());
        }
    }
    @Test void smartRecoveryRebuildsFromPageOneInsteadOfSplicingOldResults() {
        var sent = new ArrayList<String>(); var reset = new int[1];
        CaptureEngine.Sink sink = new CaptureEngine.Sink() {
            public void send(String s) { sent.add(s); }
            public void line(int p, MessageData m) {}
            public void page(CaptureEngine.Page p) {}
            public void finish(CaptureEngine.Snapshot s) {}
            public void restartExport() { reset[0]++; }
        };
        var c = new Config(); var e = new CaptureEngine(LookupCommand.parse("co l a:sign t:7d").orElseThrow(), c.settings(), sink, "server", 1);
        e.configureInspectionRecovery(true, 4000);
        e.accept(MessageData.plain(CaptureEngineTest.HEADER, 1), 1);
        e.accept(new MessageData("1/h ago - jose: old", List.of("2026-10-10 12:00:00 UTC"), List.of(), 1), 1);
        e.accept(MessageData.plain("Page 1/3", 1), 1);
        e.blockInteraction(2); e.accept(MessageData.plain(CaptureEngineTest.HEADER, 2), 2);
        e.tick(4002); e.tick(4252);
        assertEquals(1, reset[0]); assertEquals(0, e.completedPages()); assertEquals(1, e.expectedPage());
        assertEquals(List.of("co l a:sign t:7d"), sent);
    }
    @Test void queueCannotRunCustomCommandsUntilRecoveredCaptureIsExported() {
        var task = new TaskDefinition(); task.command = "co l a:sign t:7d";
        task.commands.add(new TaskCommand("/after", 750));
        var commands = new ArrayList<String>();
        var queue = new TaskQueue(List.of(task), 3000, new TaskQueue.Host() {
            public boolean start(TaskDefinition t) { return true; }
            public boolean command(String command) { commands.add(command); return true; }
            public void finished() {}
        });
        queue.start();
        var h = harness(); h.body(); h.footer(1, 2); h.engine.blockInteraction(h.now); h.receive(CaptureEngineTest.HEADER);
        queue.tick(100000); assertEquals(TaskQueue.Phase.CAPTURE, queue.snapshot(100000).phase()); assertTrue(commands.isEmpty());
        h.after(4000); h.after(250); h.body(); h.footer(1, 2); h.after(1500); h.body(); h.footer(2, 2);
        queue.exported(h.finalResult.outcome(), h.now); queue.tick(h.now + 749); assertTrue(commands.isEmpty());
        queue.tick(h.now + 750); assertEquals(List.of("after"), commands);
    }
    @Test void uninterruptedNoiseEventuallyStopsAndSlowDiskBlocksNewPageRequests() {
        var h = harness(); h.engine.blockInteraction(h.now); h.receive(CaptureEngineTest.HEADER);
        h.after(4000);
        for (int i = 0; i < 30; i++) { h.receive(CaptureEngineTest.ENTRY); h.after(1000); }
        assertEquals(CaptureEngine.Outcome.MISMATCH, h.finalResult.outcome()); assertTrue(h.sent.isEmpty());
        final boolean[] ready = {false}; var sent = new ArrayList<String>();
        var e = new CaptureEngine(LookupCommand.parse("co l a:sign t:7d").orElseThrow(), ProgressiveCaptureTest.normal().settings(),
                new CaptureEngine.Sink() {
                    public boolean ready() { return ready[0]; }
                    public void send(String command) { sent.add(command); }
                    public void line(int p, MessageData m) {}
                    public void page(CaptureEngine.Page p) {}
                    public void finish(CaptureEngine.Snapshot s) { fail("Must wait for storage"); }
                }, "server", 1);
        e.accept(MessageData.plain(CaptureEngineTest.HEADER, 1), 1);
        e.accept(MessageData.plain(CaptureEngineTest.ENTRY, 1), 1);
        e.accept(MessageData.plain("Page 1/2", 1), 1);
        e.tick(100000); assertTrue(sent.isEmpty()); assertTrue(e.active());
        ready[0] = true; e.tick(100001); assertEquals(List.of("co l 2"), sent);
    }
}
