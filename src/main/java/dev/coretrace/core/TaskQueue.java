package dev.coretrace.core;

import java.util.List;

/** Tick-driven queue: export completion, custom command delay, then inter-task delay. */
public final class TaskQueue {
    public interface Host {
        boolean start(TaskDefinition task);
        boolean command(String command);
        void finished();
        default boolean checkpoint(State state) { return true; }
    }
    public enum Phase { CAPTURE, CUSTOM, NEXT, STOPPED }
    public record State(List<TaskDefinition> tasks, int delay, int index, int commandIndex, Phase phase, long remainingMs) {}
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
    private void begin() { phase = Phase.CAPTURE; if (!checkpoint(0) || !host.start(tasks.get(index))) cancel(); }
    public State snapshot(long now) {
        return new State(tasks, delay, index, commandIndex, phase,
                phase == Phase.CUSTOM || phase == Phase.NEXT ? Math.max(0, due - now) : 0);
    }
    public void restore(State state, long now) {
        if (state.index() < 0 || state.index() >= tasks.size() || state.phase() == null || state.phase() == Phase.STOPPED
                || state.commandIndex() < 0 || state.remainingMs() < 0 || state.remainingMs() > 3_600_000
                || (state.phase() == Phase.CUSTOM && state.commandIndex() >= tasks.get(state.index()).commands.size())
                || (state.phase() == Phase.NEXT && state.index() + 1 >= tasks.size()))
            throw new IllegalArgumentException("Invalid queue recovery state");
        index = state.index(); commandIndex = state.commandIndex(); phase = state.phase(); due = now + state.remainingMs();
        if (phase == Phase.CAPTURE) begin();
    }
    private boolean checkpoint(long now) {
        if (host.checkpoint(snapshot(now))) return true;
        cancel(); return false;
    }
    public void exported(CaptureEngine.Outcome outcome, long now) {
        if (phase != Phase.CAPTURE) return;
        if (!outcome.successful()) { cancel(); return; }
        TaskDefinition task = tasks.get(index);
        commandIndex = 0;
        if (task.commands.isEmpty()) afterTask(now);
        else { phase = Phase.CUSTOM; due = now + task.commands.getFirst().delayMs; }
        checkpoint(now);
    }
    public void tick(long now) {
        if (!active() || phase == Phase.CAPTURE || now < due) return;
        if (phase == Phase.CUSTOM) {
            String command = tasks.get(index).commands.get(commandIndex).command.strip().replaceFirst("^/", "");
            var commands = tasks.get(index).commands;
            // Reserve the command durably BEFORE dispatch: an uncertain send is never replayed after a crash.
            boolean last = false;
            if (++commandIndex < commands.size()) due = now + commands.get(commandIndex).delayMs;
            else if (index + 1 < tasks.size()) { phase = Phase.NEXT; due = now + delay; }
            else { phase = Phase.STOPPED; last = true; }
            if (!checkpoint(now)) return;
            if (!host.command(command)) { cancel(); return; }
            if (last) host.finished();
        } else { index++; begin(); }
    }
    private void afterTask(long now) {
        if (index + 1 == tasks.size()) { cancel(); host.finished(); }
        else { phase = Phase.NEXT; due = now + delay; }
    }
    public void cancel() { phase = Phase.STOPPED; }
}
