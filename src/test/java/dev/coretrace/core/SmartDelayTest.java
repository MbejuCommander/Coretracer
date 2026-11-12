package dev.coretrace.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class SmartDelayTest {
    @TempDir Path temp;
    private static void confirm(SmartDelay delay, long time) { delay.command(time - 100); delay.confirmed(time); }
    @Test void defaultsAndOldConfigKeepManualPacingAndEnableEta() {
        Config c = new com.google.gson.Gson().fromJson("{}", Config.class);
        c.normalize(); assertFalse(c.smartDelay); assertFalse(c.smartDelayPerServer); assertTrue(c.showEta);
        assertEquals(240, c.smartDelayMinimumMs); assertEquals(5, c.smartDelayWindowSeconds);
        assertArrayEquals(new int[] {30, 60, 90, 120}, c.smartDelayHoldSeconds);
        assertEquals("Kicked for spamming", c.smartDelayKickMessage);
    }
    @Test void loweringRequiresSixObservedWindowsAndNeverCrossesMinimum() {
        var d = new SmartDelay(new SmartDelay.State(), 750, 500, 5);
        for (long t = 1000; t <= 30000; t += 1000) confirm(d, t);
        assertEquals(750, d.delay()); confirm(d, 31000); assertEquals(675, d.delay());
        for (long t = 32000; t < 900000; t += 1000) confirm(d, t);
        assertEquals(500, d.delay()); assertEquals(500, d.state().stableMinimum);
    }
    @Test void spamRaisesDelayAndPersistsActivityHold() {
        var d = new SmartDelay(new SmartDelay.State(), 750);
        d.command(1000);
        assertTrue(d.disconnected("Server: KICKED FOR SPAMMING!", SmartDelay.DEFAULT_REASON, 1100));
        assertEquals(1125, d.delay()); assertEquals(750, d.state().badDelay); assertEquals(30000, d.state().stableHoldRemaining);
        assertEquals(6100, d.state().cooldownUntil); assertEquals(0, d.state().stableMinimum);
        assertFalse(d.disconnected(SmartDelay.DEFAULT_REASON, SmartDelay.DEFAULT_REASON, 1200));
        for (long t = 10000; t < 2000000; t += 1000) confirm(d, t);
        assertTrue(d.delay() < 1000);
    }
    @Test void unrelatedAndStaleDisconnectsDoNotPenalize() {
        var d = new SmartDelay(new SmartDelay.State(), 750);
        d.command(1000);
        assertFalse(d.disconnected("Timed out", SmartDelay.DEFAULT_REASON, 1100));
        assertFalse(d.disconnected(SmartDelay.DEFAULT_REASON, SmartDelay.DEFAULT_REASON, 10000));
        assertEquals(750, d.delay());
        assertFalse(SmartDelay.matches("anything", " "));
        assertTrue(SmartDelay.matches("Expulsado: demasiados comandos", "DEMASIADOS COMANDOS"));
    }
    @Test void pauseAndIdleDoNotCountAsCleanWindows() {
        var d = new SmartDelay(new SmartDelay.State(), 750);
        confirm(d, 1000); confirm(d, 100000); assertEquals(0, d.state().stableMinimum);
        d.interrupt(); confirm(d, 200000); assertEquals(750, d.delay());
    }
    @Test void windowIsConfigurableWithoutAFixedCommandQuota() {
        var d = new SmartDelay(new SmartDelay.State(), 750, 300, 2);
        for (long t = 1000; t <= 13000; t += 1000) confirm(d, t);
        assertEquals(675, d.delay());
        assertEquals(2, d.commandsInWindow(13000)); assertEquals(0, d.commandsInWindow(16000));
        assertEquals(240, SmartDelay.parseMinimum("")); assertEquals(5, SmartDelay.parseWindow(""));
        assertThrows(IllegalArgumentException.class, () -> SmartDelay.parseMinimum("239"));
        assertThrows(IllegalArgumentException.class, () -> SmartDelay.parseWindow("0"));
        assertThrows(IllegalArgumentException.class, () -> SmartDelay.parseWindow("301"));
    }
    @Test void capsSurviveManyKicks() {
        var d = new SmartDelay(new SmartDelay.State(), 750);
        for (int i = 0; i < 30; i++) { d.command(i * 100000L); d.disconnected("spam", "spam", i * 100000L + 1); }
        assertEquals(30000, d.delay()); assertEquals(30000, d.state().badDelay); assertEquals(120000, d.state().stableHoldRemaining);
        assertEquals(30000, d.state().safetyMaximum);
    }
    @Test void configurableHoldPeriodsApplyToEachKickAndPersist() throws Exception {
        int[] custom = SmartDelay.parseHoldSeconds(new String[] {"15", "30", "", "120"});
        assertArrayEquals(new int[] {15, 30, 90, 120}, custom);
        assertThrows(IllegalArgumentException.class, () -> SmartDelay.parseHoldSeconds(new String[] {"30", "20", "90", "120"}));
        assertThrows(IllegalArgumentException.class, () -> SmartDelay.parseHoldSeconds(new String[] {"30", "60", "90", "121"}));
        var c = new Config(); c.smartDelayHoldSeconds = custom;
        Path path = temp.resolve("config.json"); c.save(path);
        assertArrayEquals(custom, Config.load(path).smartDelayHoldSeconds);
        var d = new SmartDelay(new SmartDelay.State(), 750, 240, 5, custom);
        for (int i = 0; i < 5; i++) {
            long now = i * 10000L + 1000;
            d.command(now);
            assertTrue(d.disconnected("Kicked for spamming", SmartDelay.DEFAULT_REASON, now + 100));
            assertEquals(custom[Math.min(i, 3)] * 1000L, d.state().stableHoldRemaining);
        }
        var legacy = new SmartDelay.State(); legacy.current = 1000;
        legacy.stableHoldRemaining = 960000; legacy.kickCount = 4;
        assertEquals(120000, new SmartDelay(legacy, 750, 240, 5, custom).state().stableHoldRemaining);
        var earlierKick = new SmartDelay.State(); earlierKick.current = 1000;
        earlierKick.stableHoldRemaining = 240000; earlierKick.kickCount = 2;
        assertEquals(30000, new SmartDelay(earlierKick, 750, 240, 5, custom).state().stableHoldRemaining);
    }
    @Test void globalAndServerProfilesPersistIndependentlyAndResetOnlySelected() throws Exception {
        var store = new SmartDelayStore();
        store.state(false, "one").current = 1700;
        assertEquals(1700, store.state(false, "two").current);
        store.state(true, "ONE").current = 900;
        store.state(true, "two").current = 1200;
        Path path = temp.resolve("smart-delay.json"); SessionFiles.writeAtomic(path, store.json());
        store = SmartDelayStore.load(path);
        assertEquals(1700, store.state(false, "other").current);
        assertEquals(900, store.state(true, "one").current);
        store.reset(true, "one"); assertEquals(0, store.state(true, "one").current);
        assertEquals(1200, store.state(true, "two").current);
        assertEquals(1700, store.state(false, "one").current);
    }
    @Test void queueUsesEffectiveSettingsAndLeavesOtherDelaysAlone() {
        Config c = new Config(); c.smartDelay = true; c.smartDelayMinimumMs = 400; c.smartDelayWindowSeconds = 10;
        c.smartDelayHoldSeconds = new int[] {20, 40, 60, 80};
        var task = new TaskDefinition(); task.command = "co l a:chat";
        task.commands.add(new TaskCommand("say hello", 750));
        assertTrue(task.effective(c).smartDelay); assertEquals(400, task.effective(c).smartDelayMinimumMs);
        assertEquals(10, task.effective(c).smartDelayWindowSeconds);
        assertArrayEquals(c.smartDelayHoldSeconds, task.effective(c).smartDelayHoldSeconds);
        assertEquals(750, task.commands.getFirst().delayMs); assertEquals(3000, c.taskDelayMs);
        task.universalSettings = false; task.taskSettings = new Config();
        assertFalse(task.effective(c).smartDelay);
    }
    @Test void holdCountsOnlyActiveIntervalsAndSurvivesReload() {
        var d = new SmartDelay(new SmartDelay.State(), 600);
        d.command(1000); d.disconnected("spam", "spam", 1100);
        assertEquals(900, d.delay());
        confirm(d, 10000); confirm(d, 11000);
        assertEquals(29000, d.state().stableHoldRemaining);
        d.interrupt(); confirm(d, 900000);
        assertEquals(29000, d.state().stableHoldRemaining);
        var resumed = new SmartDelay(SmartDelay.copy(d.state()), 750);
        confirm(resumed, 1000000);
        assertEquals(29000, resumed.state().stableHoldRemaining);
        for (long t=1001000; t<=1029000; t+=1000) confirm(resumed,t);
        assertEquals(900, resumed.delay());
        assertEquals(0, resumed.state().stableHoldRemaining);
        for (long t=1030000; t<=1059000; t+=1000) confirm(resumed,t);
        assertEquals(882, resumed.delay());
    }
    @Test void cautiousProbingCanPassOldBadDelayButNotConfiguredMinimum() {
        var state = new SmartDelay.State(); state.current=900; state.badDelay=600; state.lastKickAt=1; state.kickCount=1;
        var d=new SmartDelay(state,1500,240);
        for(long t=1000;t<=31000;t+=1000) confirm(d,t);
        assertEquals(882,d.delay());
        for(long t=32000;t<3000000;t+=1000) confirm(d,t);
        assertEquals(240,d.delay());
    }
    @Test void recentThreePageDelaysIncludeMinimumAverageMaximumAndNext() {
        var d = new SmartDelay(new SmartDelay.State(), 550, 240);
        d.pageCommand(); d.state().current = 440; d.pageCommand();
        d.state().current = 240; d.pageCommand();
        assertEquals(new SmartDelay.Recent(264, 425, 550, 3), d.recent());
        assertEquals(240, d.delay());
        d.state().current = 300; d.pageCommand();
        assertEquals(new SmartDelay.Recent(264, 342, 462, 3), d.recent());
        var restored = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(d.state()), SmartDelay.State.class);
        assertEquals(d.recent(), new SmartDelay(restored, 750).recent());
    }
    @Test void eachPageUsesSmallDeterministicVariationAndStatisticsSurviveResume() {
        var d = new SmartDelay(new SmartDelay.State(), 750, 240);
        assertEquals(750, d.delay()); d.pageCommand();
        assertEquals(788, d.delay()); d.pageCommand();
        assertEquals(825, d.delay()); d.pageCommand();
        assertEquals(new SmartDelay.Recent(750, 788, 825, 3), d.recent());
        assertEquals(750, d.delay());
        var resumed = new SmartDelay(SmartDelay.copy(d.state()), 750);
        assertEquals(d.recent(), resumed.recent());
        assertEquals(750, resumed.delay());
        resumed.pageCommand(); assertEquals(788, resumed.delay());
        assertEquals(new SmartDelay.Recent(750, 788, 825, 3), resumed.recent());
    }
    @Test void spamKickUsesActualLastPageWaitAndRisesByFiftyPercent() {
        var d = new SmartDelay(new SmartDelay.State(), 750);
        d.pageCommand(); d.pageCommand(); d.pageCommand();
        d.command(1000);
        assertTrue(d.disconnected("Kicked for spamming", SmartDelay.DEFAULT_REASON, 1100));
        assertEquals(1238, d.delay());
        assertEquals(825, d.state().badDelay);
        assertEquals(0, d.state().nextSlot);
        assertEquals(30000, d.state().stableHoldRemaining);
        assertFalse(d.disconnected("Kicked for spamming", SmartDelay.DEFAULT_REASON, 1200));
    }
    @Test void lowMinimumAndMaximumKeepVariationInsideBounds() {
        var low = new SmartDelay(new SmartDelay.State(), 240);
        low.pageCommand(); assertEquals(252, low.delay());
        low.pageCommand(); assertEquals(264, low.delay());
        var high = new SmartDelay(new SmartDelay.State(), 30000);
        for (int i = 0; i < 4; i++) { assertEquals(30000, high.delay()); high.pageCommand(); }
    }
    @Test void engineSchedulesEachFollowingPageWithTheNextVariableWait() {
        class AdaptiveHarness extends CaptureEngineTest.Harness {
            final SmartDelay pacing = new SmartDelay(new SmartDelay.State(), 750);
            @Override public void send(String command) { pacing.pageCommand(); super.send(command); }
        }
        var h = new AdaptiveHarness(); h.engine.configureDelay(h.pacing::delay);
        h.body(); h.footer(1, 4);
        h.after(749); assertTrue(h.sent.isEmpty());
        h.after(1); assertEquals("co l 2", h.sent.getLast());
        h.body(); h.footer(2, 4);
        h.after(787); assertEquals(1, h.sent.size());
        h.after(1); assertEquals("co l 3", h.sent.getLast());
        h.body(); h.footer(3, 4);
        h.after(824); assertEquals(2, h.sent.size());
        h.after(1); assertEquals("co l 4", h.sent.getLast());
        assertEquals(new SmartDelay.Recent(750, 788, 825, 3), h.pacing.recent());
    }
    @Test void newProfilesAllow240AndLegacyProfilesWithoutSamplesLoad() {
        assertEquals(240, new SmartDelay(new SmartDelay.State(), 750).state().floor);
        var old = new com.google.gson.Gson().fromJson("{\"current\":875,\"floor\":300}", SmartDelay.State.class);
        var learner = new SmartDelay(old, 1500);
        assertEquals(240, learner.state().floor);
        assertEquals(0, learner.recent().count());
        assertEquals(240, SmartDelay.parseMinimum(""));
    }
    @Test void engineCanUse300msButManualCaptureKeepsItsDelay() {
        var h = new CaptureEngineTest.Harness(); h.engine.configureDelay(() -> 300);
        h.body(); h.footer(1, 3); h.after(299); assertTrue(h.sent.isEmpty());
        h.after(1); assertEquals("co l 2", h.sent.getLast());
        var manual = new CaptureEngineTest.Harness(); manual.body(); manual.footer(1, 3);
        manual.after(300); assertTrue(manual.sent.isEmpty());
        manual.after(1200); assertEquals("co l 2", manual.sent.getLast());
    }
}
