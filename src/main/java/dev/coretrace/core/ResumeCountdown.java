package dev.coretrace.core;

/** No blocking sleep, one notification per elapsed second, safe even for very large delays. */
public final class ResumeCountdown {
    private final long started, delay;
    private long lastSecond;
    public ResumeCountdown(long now, long delay) {
        if (delay < 0) throw new IllegalArgumentException("Negative delay");
        this.started = now; this.delay = delay;
    }
    public long delay() { return delay; }
    public long remaining(long now) { return Math.max(0, delay - Math.max(0, now - started)); }
    public long seconds(long now) { long r = remaining(now); return r / 1000 + (r % 1000 == 0 ? 0 : 1); }
    public boolean soundDue(long now) {
        long elapsed = Math.min(delay, Math.max(0, now - started)) / 1000;
        if (elapsed <= lastSecond) return false;
        lastSecond = elapsed; return true;
    }
    public static long parseDelay(String value) {
        long delay = value.isBlank() ? 4000 : Long.parseLong(value.strip());
        if (delay < 0) throw new IllegalArgumentException("Negative delay");
        return delay;
    }
}
