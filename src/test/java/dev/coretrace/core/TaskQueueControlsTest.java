package dev.coretrace.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskQueueControlsTest {
    private static TaskDefinition task(String name) {
        var task = new TaskDefinition(); task.command = "/co l a:sign t:7d"; task.csvName = name;
        task.commands.add(new TaskCommand("/after", 1000));
        return task;
    }
    private static final class Host implements TaskQueue.Host {
        final List<String> events = new ArrayList<>();
        Boolean complete;
        boolean ready = true;
        public boolean start(TaskDefinition task) { events.add(task.csvName); return true; }
        public boolean command(String command) { events.add(command); return true; }
        public void finished() { throw new AssertionError("Completion state required"); }
        public void finished(boolean complete) { this.complete = complete; events.add("finished"); }
        public boolean readyForNextTask() { return ready; }
    }

    @Test void pausedCustomDelaySurvivesRestartAndResumesWithRemainingTime() {
        var tasks = List.of(task("one")); var first = new Host();
        var queue = new TaskQueue(tasks, 3000, first); queue.start();
        queue.exported(CaptureEngine.Outcome.COMPLETE, 0);
        assertTrue(queue.setPaused(true, 100));
        var saved = queue.snapshot(10000);
        assertTrue(saved.paused()); assertEquals(900, saved.remainingMs());
        queue.tick(10000); assertEquals(List.of("one"), first.events);
        var restoredHost = new Host(); var restored = new TaskQueue(tasks, 3000, restoredHost);
        restored.restore(saved, 5000); restored.tick(10000);
        assertTrue(restoredHost.events.isEmpty());
        assertTrue(restored.setPaused(false, 10000));
        restored.tick(10899); assertTrue(restoredHost.events.isEmpty());
        restored.tick(10900); assertEquals(List.of("after", "finished"), restoredHost.events);
        assertEquals(Boolean.TRUE, restoredHost.complete);
    }

    @Test void cancellingCurrentCaptureSkipsItsCommandsAndKeepsQueuePartial() {
        var host = new Host(); var queue = new TaskQueue(List.of(task("one"), task("two")), 3000, host);
        queue.start(); assertTrue(queue.skipCurrent(100));
        queue.tick(3099); assertEquals(List.of("one"), host.events);
        queue.tick(3100); assertEquals(List.of("one", "two"), host.events);
        queue.exported(CaptureEngine.Outcome.COMPLETE, 3200);
        queue.tick(4200);
        assertEquals(List.of("one", "two", "after", "finished"), host.events);
        assertEquals(Boolean.FALSE, host.complete);
    }

    @Test void cancellingAfterCaptureSkipsPendingCustomCommands() {
        var host = new Host(); var queue = new TaskQueue(List.of(task("one"), task("two")), 3000, host);
        queue.start(); queue.exported(CaptureEngine.Outcome.COMPLETE, 100);
        assertTrue(queue.canSkipCurrent()); assertTrue(queue.skipCurrent(200));
        queue.tick(3199); assertEquals(List.of("one"), host.events);
        queue.tick(3200); assertEquals(List.of("one", "two"), host.events);
    }

    @Test void cancelledCaptureWaitsForTimeoutAndLateResponseQuarantine() {
        var host = new Host(); var queue = new TaskQueue(List.of(task("one"), task("two")), 3000, host);
        queue.start(); assertTrue(queue.skipCurrent(100, 30000));
        assertEquals(30000, queue.snapshot(100).remainingMs());
        queue.tick(30099); assertEquals(List.of("one"), host.events);
        host.ready = false; queue.tick(30100); assertEquals(List.of("one"), host.events);
        host.ready = true; queue.tick(35000); assertEquals(List.of("one", "two"), host.events);
    }

    @Test void pausingDuringCaptureFreezesFollowingCommandDelay() {
        var host = new Host(); var queue = new TaskQueue(List.of(task("one")), 3000, host);
        queue.start(); assertTrue(queue.setPaused(true, 100));
        queue.exported(CaptureEngine.Outcome.COMPLETE, 200);
        queue.tick(10000); assertEquals(List.of("one"), host.events);
        assertTrue(queue.setPaused(false, 5000));
        queue.tick(5999); assertEquals(List.of("one"), host.events);
        queue.tick(6000); assertEquals(List.of("one", "after", "finished"), host.events);
    }
}
