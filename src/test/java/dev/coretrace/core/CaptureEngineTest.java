package dev.coretrace.core;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CaptureEngineTest {
    static final String HEADER = "----- CoreProtect | Lookup Results -----";
    static final String ENTRY = "0.05/h ago - jose broke minecraft:stone.";
    static class Harness implements CaptureEngine.Sink {
        long now = 1000;
        final List<String> sent = new ArrayList<>();
        final List<MessageData> logged = new ArrayList<>();
        final List<CaptureEngine.Page> committed = new ArrayList<>();
        CaptureEngine.Snapshot finalResult;
        final CaptureEngine engine;
        Harness() { this(500); }
        Harness(int limit) { this("co l a:-block u:jose t:3d", Language.SPANISH, limit); }
        Harness(String command, Language language) { this(command, language, 500); }
        Harness(String command, Language language, int limit) { this(command, language, limit, true); }
        Harness(String command, Language language, int limit, boolean acceptSinglePage) {
            engine = new CaptureEngine(LookupCommand.parse(command).orElseThrow(),
                    new CaptureEngine.Settings(1500,30000,3500,limit,language,acceptSinglePage), this, "example.test", now);
        }
        boolean receive(String s) { return engine.accept(MessageData.plain(s, now),now); }
        void after(long ms) { now += ms; engine.tick(now); }
        void body() { receive(HEADER); receive(ENTRY); receive("    ^ (x12/y64/z-40/world)"); }
        void footer(int p,int total) { receive("◀ Page " + p + "/" + total + " ▶ (1 | 2 | 3)"); }
        @Override public void send(String s) { sent.add(s); }
        @Override public void line(int p,MessageData m) { logged.add(m); }
        @Override public void page(CaptureEngine.Page p) { committed.add(p); }
        @Override public void finish(CaptureEngine.Snapshot s) { assertNull(finalResult,"finish must be called once"); finalResult=s; }
    }

    @Test void automaticallyCapturesFifteenPagesAndStopsWithoutPageSixteen() {
        Harness h = new Harness();
        for(int page=1;page<=15;page++) {
            h.body(); h.footer(page,15);
            if(page<15) { h.after(1499);assertEquals(page-1,h.sent.size());h.after(1);assertEquals("co l "+(page+1),h.sent.getLast()); }
        }
        assertEquals(14,h.sent.size()); assertEquals(15,h.finalResult.pages().size());
        assertEquals(CaptureEngine.Outcome.COMPLETE,h.finalResult.outcome());
        h.after(100000);assertEquals(14,h.sent.size());assertEquals(15,Report.rows(h.finalResult).size());
    }
    @Test void ignoresPlayerLikeAndUnrelatedSystemMessages() {
        Harness h=new Harness();
        assertFalse(h.receive("<jose> Page 1/9"));
        h.body();assertFalse(h.receive("[Server] Reinicio en diez minutos"));
        assertFalse(h.receive("<ana> hola"));h.footer(1,1);
        assertEquals(4,h.logged.size());
    }
    @Test void identicalEventsRemainSeparateRecords() {
        Harness h=new Harness();h.receive(HEADER);h.receive(ENTRY);h.receive(ENTRY);h.footer(1,1);
        assertEquals(2,Report.rows(h.finalResult).size());
    }
    @Test void footerCannotDriveRequestsWithoutAHeader() {
        Harness h=new Harness();h.footer(1,20);h.after(2000);assertTrue(h.sent.isEmpty());assertNull(h.finalResult);
    }
    @Test void duplicateFooterDoesNotDuplicateAPageOrScheduleTwice() {
        Harness h=new Harness();h.body();h.footer(1,3);h.footer(1,3);h.after(1500);
        assertEquals(List.of("co l 2"),h.sent);assertEquals(1,h.committed.size());
    }
    @Test void waitsForDelayedFooterInsteadOfSendingBlindly() {
        Harness h=new Harness();h.body();h.after(3000);assertTrue(h.sent.isEmpty());h.footer(1,3);h.after(1500);
        assertEquals(List.of("co l 2"),h.sent);
    }
    @Test void singlePageWithoutFooterIsExplicitlyInferredWhenOptionIsOff() {
        Harness h=new Harness("co l a:chat t:3d", Language.SPANISH, 500, false);h.body();h.after(3500);
        assertEquals(CaptureEngine.Outcome.SINGLE_PAGE_INFERRED,h.finalResult.outcome());
        assertFalse(h.finalResult.pages().getFirst().confirmed());assertTrue(h.sent.isEmpty());
        assertTrue(Report.summary(h.finalResult).contains("NO CONFIRMADO"));
    }
    @Test void defaultSinglePageIsSavedAndAcceptedAfterSettleWithoutClaimingServerConfirmation() {
        Harness h = new Harness(); h.body(); h.after(3499); assertNull(h.finalResult);
        h.after(1);
        assertEquals(CaptureEngine.Outcome.SINGLE_PAGE_ACCEPTED, h.finalResult.outcome());
        assertTrue(h.finalResult.outcome().successful()); assertEquals(1, h.finalResult.totalPages());
        assertFalse(h.finalResult.pages().getFirst().confirmed());
        assertEquals(1, Report.rows(h.finalResult).size()); assertTrue(h.sent.isEmpty());
    }
    @Test void lateEntriesRestartSinglePageSettleTimer() {
        Harness h = new Harness(); h.body(); h.after(3000); h.receive(ENTRY); h.after(3499);
        assertNull(h.finalResult); h.after(1); assertEquals(2, Report.rows(h.finalResult).size());
    }
    @Test void startingAtLaterPageIsNeverAcceptedAsCompleteSinglePage() {
        Harness h = new Harness("co l 3", Language.ENGLISH); h.body(); h.after(3500);
        assertEquals(CaptureEngine.Outcome.SINGLE_PAGE_INFERRED, h.finalResult.outcome());
    }
    @Test void missingSecondPageFooterIsPartialNotSinglePage() {
        Harness h=new Harness();h.body();h.footer(1,5);h.after(1500);h.body();h.after(3500);
        assertNull(h.finalResult);h.after(27000);
        assertEquals(CaptureEngine.Outcome.TIMEOUT,h.finalResult.outcome());
        assertEquals(1,h.finalResult.pages().size());assertEquals(3,h.finalResult.pending().size());
    }
    @Test void noResponseTimesOutWithoutRetries() {
        Harness h=new Harness();h.after(30000);assertEquals(CaptureEngine.Outcome.TIMEOUT,h.finalResult.outcome());
        assertTrue(h.sent.isEmpty());
    }
    @Test void pauseAllowsCurrentPageToFinishButSendsNothingElse() {
        Harness h=new Harness();h.body();h.engine.togglePause(h.now);h.footer(1,3);h.after(100000);
        assertTrue(h.sent.isEmpty());assertEquals(1,h.committed.size());h.engine.togglePause(h.now);
        h.after(1499);assertTrue(h.sent.isEmpty());h.after(1);assertEquals(List.of("co l 2"),h.sent);
    }
    @Test void pauseFreezesResponseTimeout() {
        Harness h=new Harness();h.engine.togglePause(h.now);h.after(100000);assertNull(h.finalResult);
        h.engine.togglePause(h.now);h.after(29999);assertNull(h.finalResult);h.after(1);
        assertEquals(CaptureEngine.Outcome.TIMEOUT,h.finalResult.outcome());
    }
    @Test void cancellationKeepsCurrentUnfinishedPage() {
        Harness h=new Harness();h.body();h.engine.end(CaptureEngine.Outcome.CANCELLED,"cancel",h.now);
        assertEquals(3,h.finalResult.pending().size());h.after(30000);assertTrue(h.sent.isEmpty());
        assertFalse(Report.rows(h.finalResult).getFirst().confirmedPage());
    }
    @Test void disconnectKeepsReceivedPages() {
        Harness h=new Harness();h.body();h.footer(1,3);h.engine.end(CaptureEngine.Outcome.DISCONNECTED,"disconnect",h.now);
        assertEquals(1,h.finalResult.pages().size());assertEquals(CaptureEngine.Outcome.DISCONNECTED,h.finalResult.outcome());
    }
    @Test void wrongPageIsNotCountedAsSuccessfullyCaptured() {
        Harness h=new Harness();h.body();h.footer(1,3);h.after(1500);h.body();h.footer(3,3);
        assertEquals(CaptureEngine.Outcome.MISMATCH,h.finalResult.outcome());assertEquals(1,h.finalResult.pages().size());
    }
    @Test void changedTotalStopsToAvoidMixingLookupCaches() {
        Harness h=new Harness();h.body();h.footer(1,3);h.after(1500);h.body();h.footer(2,4);
        assertEquals(CaptureEngine.Outcome.MISMATCH,h.finalResult.outcome());
    }
    @Test void secondHeaderWithoutClosureStops() {
        Harness h=new Harness();h.body();h.receive(HEADER);assertEquals(CaptureEngine.Outcome.MISMATCH,h.finalResult.outcome());
    }
    @Test void limitSavesPartialAndDoesNotRequestBeyondLimit() {
        Harness h=new Harness(1);h.body();h.footer(1,15);h.after(5000);
        assertEquals(CaptureEngine.Outcome.LIMIT,h.finalResult.outcome());assertTrue(h.sent.isEmpty());
    }
    @Test void recognizedPermissionDenialTerminatesImmediately() {
        Harness h=new Harness();h.receive("CoreProtect - You do not have permission to use that command.");
        assertEquals(CaptureEngine.Outcome.SERVER_ERROR,h.finalResult.outcome());assertTrue(h.sent.isEmpty());
    }
    @Test void emptyQueryCompletesWithoutInventingAPage() {
        Harness h=new Harness();h.receive("CoreProtect - No results found.");
        assertEquals(CaptureEngine.Outcome.EMPTY,h.finalResult.outcome());assertTrue(h.finalResult.pages().isEmpty());
    }
    @Test void headerWithoutRecognizableEventsNeverBecomesSuccess() {
        Harness h=new Harness();h.receive(HEADER);h.receive("unexpected format");h.footer(1,2);
        assertEquals(CaptureEngine.Outcome.MISMATCH,h.finalResult.outcome());
    }
}
