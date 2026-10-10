package dev.coretrace.core;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskQueueRecoveryTest {
    private static TaskDefinition task(String name) {
        var task = new TaskDefinition(); task.command = "/co l a:sign t:7d"; task.csvName = name;
        task.commands.add(new TaskCommand("/first", 100)); task.commands.add(new TaskCommand("/second", 200));
        return task;
    }
    static class Host implements TaskQueue.Host {
        List<String> events = new ArrayList<>(); TaskQueue.State saved; boolean durable = true;
        public boolean start(TaskDefinition t) { events.add(t.csvName); return true; }
        public boolean command(String command) { events.add(command); return true; }
        public void finished() { events.add("done"); }
        public boolean checkpoint(TaskQueue.State state) { saved = state; return durable; }
    }
    @Test void crashAfterFirstCustomCommandResumesSecondWithoutRepeatingCaptureOrCommand() {
        var tasks = List.of(task("one"), task("two")); var first = new Host();
        var queue = new TaskQueue(tasks, 3000, first); queue.start(); queue.exported(CaptureEngine.Outcome.COMPLETE, 1000);
        queue.tick(1100); assertEquals(List.of("one", "first"), first.events);
        assertEquals(1, first.saved.commandIndex());
        var next = new Host(); var restored = new TaskQueue(tasks, 3000, next);
        restored.restore(first.saved, 10000); restored.tick(10199); assertTrue(next.events.isEmpty());
        restored.tick(10200); assertEquals(List.of("second"), next.events);
        restored.tick(13199); assertEquals(1, next.events.size()); restored.tick(13200);
        assertEquals(List.of("second", "two"), next.events);
    }
    @Test void restoresCurrentTaskAndDoesNotRestartCompletedTasks() {
        var tasks = List.of(task("one"), task("two")); var host = new Host();
        var state = new TaskQueue.State(tasks, 3000, 1, 0, TaskQueue.Phase.CAPTURE, 0);
        var queue = new TaskQueue(tasks, 3000, host); queue.restore(state, 10000);
        assertEquals(List.of("two"), host.events); assertEquals(2, queue.position());
    }
    @Test void waitsRemainingTaskDelayAfterReconnect() {
        var tasks = List.of(task("one"), task("two")); var host = new Host();
        var queue = new TaskQueue(tasks, 3000, host);
        queue.restore(new TaskQueue.State(tasks, 3000, 0, 2, TaskQueue.Phase.NEXT, 1200), 5000);
        queue.tick(6199); assertTrue(host.events.isEmpty()); queue.tick(6200);
        assertEquals(List.of("two"), host.events);
    }
    @Test void cannotDispatchCommandIfItsReservationWasNotSaved() {
        var host = new Host(); var queue = new TaskQueue(List.of(task("one")), 3000, host);
        queue.start(); queue.exported(CaptureEngine.Outcome.COMPLETE, 0); host.durable = false; queue.tick(100);
        assertFalse(queue.active()); assertEquals(List.of("one"), host.events);
    }
    @Test void savesNextCommandBeforeCallingHostAndNeverReplaysLastCommand() {
        var host = new Host() {
            @Override public boolean command(String command) {
                if (command.equals("first")) assertEquals(1, saved.commandIndex());
                else assertEquals(TaskQueue.Phase.STOPPED, saved.phase());
                return super.command(command);
            }
        };
        var queue = new TaskQueue(List.of(task("one")), 3000, host);
        queue.start(); queue.exported(CaptureEngine.Outcome.COMPLETE, 0); queue.tick(100); queue.tick(300);
        assertEquals(List.of("one", "first", "second", "done"), host.events);
        assertEquals(TaskQueue.Phase.STOPPED, host.saved.phase());
    }
}
