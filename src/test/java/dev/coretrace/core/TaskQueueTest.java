package dev.coretrace.core;

import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskQueueTest {
    private TaskDefinition task(String name, String custom) {
        TaskDefinition t = new TaskDefinition(); t.command = "/co l a:command t:3d"; t.csvName = name; t.customCommand = custom; return t;
    }
    private static final class Host implements TaskQueue.Host {
        final List<String> events = new ArrayList<>();
        boolean startOk = true, commandOk = true;
        boolean checkpointOk = true;
        public boolean start(TaskDefinition t) { events.add(t.csvName); return startOk; }
        public boolean command(String c) { events.add(c); return commandOk; }
        public void finished() { events.add("done"); }
        @Override public boolean checkpoint(TaskQueue.State state) { return checkpointOk; }
    }
    @Test void waitsForExportThenCustomDelayThenTaskDelay() {
        Host h = new Host(); TaskQueue q = new TaskQueue(List.of(task("Simply1", "/simply 2"), task("Simply2", "")), 750, h);
        q.start(); q.tick(100000); assertEquals(List.of("Simply1"), h.events);
        q.exported(CaptureEngine.Outcome.COMPLETE, 100000);
        q.tick(100749); assertEquals(1, h.events.size());
        q.tick(100750); assertEquals(List.of("Simply1", "simply 2"), h.events);
        q.tick(101499); assertEquals(2, h.events.size());
        q.tick(101500); assertEquals(List.of("Simply1", "simply 2", "Simply2"), h.events);
        q.exported(CaptureEngine.Outcome.EMPTY, 102000); q.tick(102750);
        assertFalse(q.active()); assertEquals("done", h.events.getLast());
        q.tick(999999); assertEquals(4, h.events.size());
    }
    @Test void allUnsuccessfulOutcomesStopWithoutCustomCommand() {
        for (var outcome : CaptureEngine.Outcome.values()) {
            if (outcome.successful()) continue;
            Host h = new Host(); TaskQueue q = new TaskQueue(List.of(task("one", "/simply 2")), 750, h);
            q.start(); q.exported(outcome, 0); q.tick(10000);
            assertFalse(q.active(), outcome.toString()); assertEquals(List.of("one"), h.events);
        }
    }
    @Test void cancellationDuringDelayCannotResumeViaLateExportCallback() {
        Host h = new Host(); TaskQueue q = new TaskQueue(List.of(task("one", "/simply 2")), 750, h);
        q.start(); q.exported(CaptureEngine.Outcome.COMPLETE, 0); q.cancel();
        q.exported(CaptureEngine.Outcome.COMPLETE, 50); q.tick(10000);
        assertFalse(q.active()); assertEquals(List.of("one"), h.events);
    }
    @Test void failedStartOrRejectedCustomCommandStopsQueue() {
        Host h = new Host(); h.startOk = false;
        TaskQueue q = new TaskQueue(List.of(task("one", "")), 750, h); q.start(); assertFalse(q.active());
        h = new Host(); h.commandOk = false;
        q = new TaskQueue(List.of(task("one", "/simply 2"), task("two", "")), 750, h);
        q.start(); q.exported(CaptureEngine.Outcome.COMPLETE, 0); q.tick(750); q.tick(1500);
        assertFalse(q.active()); assertEquals(List.of("one", "simply 2"), h.events);
    }
    @Test void finalTaskFinishesWithoutAnExtraInterTaskDelay() {
        Host h = new Host(); TaskQueue q = new TaskQueue(List.of(task("last", "")), 60000, h);
        q.start(); q.exported(CaptureEngine.Outcome.COMPLETE, 0);
        assertFalse(q.active()); assertEquals(List.of("last", "done"), h.events);
    }
    @Test void validatesEntireQueueBeforeFirstCommand() {
        Host h = new Host(); TaskDefinition invalid = task("two", ""); invalid.command = "/co rollback t:3d";
        assertThrows(IllegalArgumentException.class, () -> new TaskQueue(List.of(task("one", ""), invalid), 750, h));
        assertTrue(h.events.isEmpty());
    }
    @Test void multipleCommandsWaitTheirOwnDelaysBeforeStartingNextTask() {
        Host h = new Host(); TaskDefinition t = task("one", "");
        t.commands.add(new TaskCommand("/first", 100)); t.commands.add(new TaskCommand("/second", 200));
        TaskQueue q = new TaskQueue(List.of(t, task("two", "")), 750, h); q.start();
        q.exported(CaptureEngine.Outcome.SINGLE_PAGE_ACCEPTED, 0);
        q.tick(99); assertEquals(List.of("one"), h.events);
        q.tick(100); q.tick(299); assertEquals(List.of("one", "first"), h.events);
        q.tick(300); q.tick(1049); assertEquals(List.of("one", "first", "second"), h.events);
        q.tick(1050); assertEquals("two", h.events.getLast());
    }
    @Test void removingAndReorderingCommandsControlsExecutionWithoutACountLimit() {
        Host h = new Host(); TaskDefinition t = task("one", "");
        for (int i = 0; i < 200; i++) t.commands.add(new TaskCommand("/cmd " + i, 0));
        t.commands.remove(0); Collections.swap(t.commands, 0, 198);
        TaskQueue q = new TaskQueue(List.of(t), 750, h); q.start(); q.exported(CaptureEngine.Outcome.COMPLETE, 0);
        for (int i = 0; i < 200; i++) q.tick(i);
        assertFalse(q.active()); assertEquals(201, h.events.size());
        assertEquals("cmd 199", h.events.get(1)); assertEquals("cmd 1", h.events.get(199));
        assertEquals("done", h.events.getLast());
    }
    @Test void cancellationBetweenCommandsSkipsRemainingCommandsAndTasks() {
        Host h = new Host(); TaskDefinition t = task("one", "");
        t.commands.add(new TaskCommand("/first", 0)); t.commands.add(new TaskCommand("/second", 100));
        TaskQueue q = new TaskQueue(List.of(t, task("two", "")), 750, h);
        q.start(); q.exported(CaptureEngine.Outcome.COMPLETE, 0); q.tick(0); q.cancel(); q.tick(5000);
        assertEquals(List.of("one", "first"), h.events);
    }
    @Test void rejectsInvalidLaterCommandsBeforeStartingAnyTask() {
        Host h = new Host(); TaskDefinition t = task("one", "");
        t.commands.add(new TaskCommand("/valid", 0)); t.commands.add(new TaskCommand("/", 750));
        assertThrows(IllegalArgumentException.class, () -> new TaskQueue(List.of(t), 750, h));
        assertTrue(h.events.isEmpty());
    }
    @Test void delayDefaultsAndBounds() {
        assertEquals(750, TaskDefinition.delay(" ")); assertEquals(0, TaskDefinition.delay("0"));
        assertEquals(1200, TaskDefinition.delay(" 1200 "));
        for (String value : List.of("-1", "1.5", "text", "3600001", "99999999999"))
            assertThrows(IllegalArgumentException.class, () -> TaskDefinition.delay(value));
    }
    @Test void pausedQueueEditsOnlyPendingTasksAndRestoresEditedOrder() {
        Host h = new Host();
        TaskQueue q = new TaskQueue(List.of(task("current", ""), task("remove", ""), task("last", "")), 750, h);
        q.start();
        assertFalse(q.replacePending(List.of(task("too early", "")), 0));
        assertTrue(q.setPaused(true, 100));
        var pending = List.of(task("new", ""), task("last", ""));
        assertTrue(q.replacePending(pending, 100));
        assertEquals(List.of("current", "new", "last"), q.snapshot(100).tasks().stream().map(t -> t.csvName).toList());
        pending.getFirst().csvName = "changed after commit";
        assertEquals("new", q.snapshot(100).tasks().get(1).csvName);
        TaskQueue.State saved = q.snapshot(100);
        Host resumedHost = new Host();
        TaskQueue restored = new TaskQueue(saved.tasks(), saved.delay(), resumedHost);
        restored.restore(saved, 1000);
        assertTrue(restored.paused());
        restored.setPaused(false, 1000);
        restored.exported(CaptureEngine.Outcome.COMPLETE, 1000);
        restored.tick(1750);
        restored.exported(CaptureEngine.Outcome.COMPLETE, 1750);
        restored.tick(2500);
        assertEquals(List.of("current", "new", "last"), resumedHost.events);
    }
    @Test void pausedQueueKeepsOldTasksWhenCheckpointFails() {
        Host h = new Host(); TaskQueue q = new TaskQueue(List.of(task("current", ""), task("next", "")), 750, h);
        q.start(); assertTrue(q.setPaused(true, 50));
        h.checkpointOk = false;
        assertFalse(q.replacePending(List.of(task("replacement", "")), 50));
        assertEquals("next", q.snapshot(50).tasks().get(1).csvName);
        assertThrows(IllegalArgumentException.class, () -> q.replacePending(List.of(new TaskDefinition()), 50));
        assertEquals("next", q.snapshot(50).tasks().get(1).csvName);
    }
    @Test void removingLastPendingTaskAfterCurrentTaskFinishesCompletesTheQueue() {
        Host h = new Host(); TaskQueue q = new TaskQueue(List.of(task("current", ""), task("next", "")), 750, h);
        q.start(); q.exported(CaptureEngine.Outcome.COMPLETE, 0);
        assertTrue(q.setPaused(true, 1));
        assertTrue(q.replacePending(List.of(), 1));
        assertFalse(q.active());
        assertEquals(List.of("current", "done"), h.events);
    }
    @Test void pendingChangesSurviveRecoveryStoreReload(@org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        Path exports = Files.createDirectories(temp.resolve("exports"));
        RecoveryStore store = new RecoveryStore(temp.resolve("recovery.json"), exports);
        TaskQueue.Host host = new TaskQueue.Host() {
            public boolean start(TaskDefinition task) { return true; }
            public boolean command(String command) { return true; }
            public void finished() {}
            public boolean checkpoint(TaskQueue.State state) {
                try { store.save(new RecoveryStore.State("example.server", null, state, "")); return true; }
                catch (Exception e) { return false; }
            }
        };
        TaskQueue queue = new TaskQueue(List.of(task("current", ""), task("old", "")), 750, host);
        queue.start(); assertTrue(queue.setPaused(true, 100));
        assertTrue(queue.replacePending(List.of(task("new", ""), task("more", "")), 100));
        RecoveryStore.State restored = store.load();
        assertTrue(restored.queue().paused());
        assertEquals(List.of("current", "new", "more"), restored.queue().tasks().stream().map(t -> t.csvName).toList());
    }
}
