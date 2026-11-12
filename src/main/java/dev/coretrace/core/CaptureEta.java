package dev.coretrace.core;

import java.util.Locale;

/** Constant-memory moving average; only confirmed pages advance progress. */
public final class CaptureEta {
    private long last = -1;
    private double average;
    private int samples;
    public void interrupt() { last = -1; average = 0; samples = 0; }
    public void page(long now) {
        if (last >= 0 && now > last) {
            double sample = now - last;
            average = samples++ == 0 ? sample : average * .8 + sample * .2;
        }
        last = now;
    }
    public long seconds(int done, int total) {
        if (samples < 2 || total <= 0) return -1;
        return (long) Math.ceil(Math.max(0, total - done) * average / 1000.0);
    }
    public static int percent(int done, int total, boolean complete) {
        return complete ? 100 : total <= 0 ? 0 : (int) Math.clamp(100L * done / total, 0, 99);
    }
    public static String time(long seconds) {
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
}
