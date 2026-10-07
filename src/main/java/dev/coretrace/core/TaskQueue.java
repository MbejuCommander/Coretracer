package dev.coretrace.core;

import java.util.List;

/** Tick-driven queue: export completion, custom command delay, then inter-task delay. */
public final class TaskQueue {
    public interface Host {
        boolean start(TaskDefinition task);
        boolean command(String command);
        void finished();
    }
    private enum Phase { CAPTURE, CUSTOM, NEXT, STOPPED }
    private final List<TaskDefinition> tasks;
    private final int delay;
    private final Host host;
    private Phase phase = Phase.STOPPED;
    private int index, commandIndex;
    private long due;
    public TaskQueue(List<TaskDefinition> tasks, int delay, Host host) {
        if (tasks.isEmpty()) throw new IllegalArgumentException("Empty queue");
        tasks.forEach(TaskDefinition::validate);
        this.tasks = List.copyOf(tasks); this.delay = delay; this.host = host;
    }
    public boolean active() { return phase != Phase.STOPPED; }
    public int position() { return index + 1; }
    public int size() { return tasks.size(); }
    public void start() { index = 0; begin(); }
    private void begin() { phase = Phase.CAPTURE; if (!host.start(tasks.get(index))) cancel(); }
    public void exported(CaptureEngine.Outcome outcome, long now) {
        if (phase != Phase.CAPTURE) return;
        if (!outcome.successful()) { cancel(); return; }
        TaskDefinition task = tasks.get(index);
        commandIndex = 0;
        if (task.commands.isEmpty()) afterTask(now);
        else { phase = Phase.CUSTOM; due = now + task.commands.getFirst().delayMs; }
    }
    public void tick(long now) {
        if (!active() || phase == Phase.CAPTURE || now < due) return;
        if (phase == Phase.CUSTOM) {
            if (!host.command(tasks.get(index).commands.get(commandIndex).command.strip().replaceFirst("^/", ""))) { cancel(); return; }
            if (active()) {
                var commands = tasks.get(index).commands;
                if (++commandIndex < commands.size()) due = now + commands.get(commandIndex).delayMs;
                else afterTask(now);
            }
        } else { index++; begin(); }
    }
    private void afterTask(long now) {
        if (index + 1 == tasks.size()) { cancel(); host.finished(); }
        else { phase = Phase.NEXT; due = now + delay; }
    }
    public void cancel() { phase = Phase.STOPPED; }
}
