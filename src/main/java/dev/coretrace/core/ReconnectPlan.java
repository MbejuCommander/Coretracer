package dev.coretrace.core;

/** One attempt per failure screen, with a bounded total and an elapsed-time delay. */
public final class ReconnectPlan {
    private final long delay;
    private final int limit;
    private int attempts;
    private long failedAt;
    private boolean waiting;
    public ReconnectPlan(long delay, int limit) {
        if (delay < 0 || limit < 1) throw new IllegalArgumentException();
        this.delay = delay; this.limit = limit;
    }
    public void failed(long now) { failedAt = now; waiting = true; }
    public boolean attempt(long now) {
        if (!waiting || attempts >= limit || now - failedAt < delay) return false;
        waiting = false; attempts++; return true;
    }
    public int attempts() { return attempts; }
}
