package dev.coretrace.core;

import java.util.ArrayDeque;
import java.util.Locale;

/** Client-thread adaptive pacing. Estimates are observations, never server guarantees. */
public final class SmartDelay {
    public static final int MIN = 240, MAX = 30000;
    public static final String DEFAULT_REASON = "Kicked for spamming";
    private static final int[] DEFAULT_HOLD_SECONDS = {30, 60, 90, 120};
    public static int[] defaultHoldSeconds() { return DEFAULT_HOLD_SECONDS.clone(); }
    public static boolean validHoldSeconds(int[] values) {
        if (values == null || values.length != 4) return false;
        for (int i = 0; i < values.length; i++)
            if (values[i] < 1 || values[i] > 120 || (i > 0 && values[i] < values[i - 1])) return false;
        return true;
    }
    public static int[] parseHoldSeconds(String[] values) {
        if (values == null || values.length != 4) throw new IllegalArgumentException("Four periods required");
        int[] result = new int[4];
        for (int i = 0; i < result.length; i++)
            result[i] = values[i].isBlank() ? DEFAULT_HOLD_SECONDS[i] : Integer.parseInt(values[i].strip());
        if (!validHoldSeconds(result)) throw new IllegalArgumentException("Periods must rise and be 1–120 seconds");
        return result;
    }
    public static final class State {
        public int current, stableMinimum, safetyMaximum, floor = MIN;
        public int[] recentDelays = new int[3];
        public int recentCount, recentCursor, nextSlot;
        public int badDelay, kickCount;
        public long stableHoldRemaining;
        public long confirmations, lastKickAt, cooldownUntil;
    }
    public record Recent(int minimum, int average, int maximum, int count) {}
    private final State state;
    private final int minimum;
    private final long window;
    private final int[] holdSeconds;
    private final ArrayDeque<Long> commands = new ArrayDeque<>();
    private long cleanSince = -1, lastConfirmed = -1, lastCommand = -1;
    private int cleanWindows, lastSentPageDelay;
    public SmartDelay(State state, int initial) {
        this(state, initial, MIN);
    }
    public SmartDelay(State state, int initial, int minimum) {
        this(state, initial, minimum, 5);
    }
    public SmartDelay(State state, int initial, int minimum, int windowSeconds) {
        this(state, initial, minimum, windowSeconds, DEFAULT_HOLD_SECONDS);
    }
    public SmartDelay(State state, int initial, int minimum, int windowSeconds, int[] holdSeconds) {
        this.state = state;
        this.window = Math.clamp(windowSeconds, 1, 300) * 1000L;
        this.minimum = Math.clamp(minimum, MIN, MAX);
        this.holdSeconds = validHoldSeconds(holdSeconds) ? holdSeconds.clone() : defaultHoldSeconds();
        state.current = Math.clamp(state.current == 0 ? initial : state.current, MIN, MAX);
        if (state.lastKickAt > 0 && state.badDelay == 0) {
            state.badDelay = Math.clamp(state.current / 2, MIN, MAX);
            state.kickCount = 1; state.stableHoldRemaining = this.holdSeconds[0] * 1000L;
        }
        state.floor = MIN; // Legacy permanent floor is replaced by cautious probing.
        state.badDelay = Math.clamp(state.badDelay, 0, MAX);
        state.kickCount = Math.clamp(state.kickCount, 0, 4);
        state.stableHoldRemaining = Math.clamp(state.stableHoldRemaining, 0,
                this.holdSeconds[Math.max(0, state.kickCount - 1)] * 1000L);
        if (state.recentDelays == null || state.recentDelays.length != 3) {
            state.recentDelays = new int[3]; state.recentCount = state.recentCursor = 0;
        }
        state.recentCount = Math.clamp(state.recentCount, 0, 3);
        state.recentCursor = Math.floorMod(state.recentCursor, 3);
        state.nextSlot = Math.floorMod(state.nextSlot, 3);
        state.current = Math.max(this.minimum, Math.max(state.current, state.floor));
        state.safetyMaximum = Math.clamp(Math.max(state.safetyMaximum, state.current * 2), MIN, MAX);
        if (state.stableMinimum < MIN || state.stableMinimum > MAX) state.stableMinimum = 0;
    }
    public State state() { return state; }
    public static State copy(State source) {
        var copy = new State();
        copy.current = source.current; copy.floor = source.floor; copy.stableMinimum = source.stableMinimum;
        copy.safetyMaximum = source.safetyMaximum;
        copy.recentDelays = source.recentDelays == null ? new int[3] : source.recentDelays.clone();
        copy.recentCount = source.recentCount; copy.recentCursor = source.recentCursor; copy.nextSlot = source.nextSlot; copy.confirmations = source.confirmations;
        copy.badDelay = source.badDelay; copy.kickCount = source.kickCount; copy.stableHoldRemaining = source.stableHoldRemaining;
        copy.lastKickAt = source.lastKickAt; copy.cooldownUntil = source.cooldownUntil;
        return copy;
    }
    /** The same planned wait is returned until a page command is actually sent. */
    public int delay() {
        int extra = state.nextSlot == 0 ? 0 : state.nextSlot == 1 ? 5 : 10;
        return Math.clamp((int) ((state.current * (100L + extra) + 50) / 100), minimum, MAX);
    }
    /** Record the pacing selected for a CoreProtect page request, never a custom command. */
    public void pageCommand() {
        lastSentPageDelay = delay();
        state.recentDelays[state.recentCursor] = lastSentPageDelay;
        state.recentCursor = (state.recentCursor + 1) % 3;
        state.recentCount = Math.min(3, state.recentCount + 1);
        state.nextSlot = (state.nextSlot + 1) % 3;
    }
    public Recent recent() {
        if (state.recentCount == 0) return new Recent(0, 0, 0, 0);
        int min = MAX, max = 0, sum = 0;
        for (int i = 0; i < state.recentCount; i++) {
            int value = state.recentDelays[i];
            min = Math.min(min, value); max = Math.max(max, value); sum += value;
        }
        return new Recent(min, Math.round((float) sum / state.recentCount), max, state.recentCount);
    }
    public void command(long now) { expire(now); commands.addLast(now); lastCommand = now; }
    private void expire(long now) { while (!commands.isEmpty() && now - commands.peekFirst() >= window) commands.removeFirst(); }
    public int commandsInWindow(long now) { expire(now); return commands.size(); }
    public void interrupt() { cleanSince = -1; lastConfirmed = -1; cleanWindows = 0; commands.clear(); }
    public void confirmed(long now) {
        if (lastCommand < 0 || now < state.cooldownUntil) return;
        expire(now);
        // Idle gaps and pauses are not evidence that a faster sustained rate is safe.
        if (lastConfirmed >= 0 && now - lastConfirmed > Math.max(window, state.current * 2L)) interrupt();
        long elapsed = lastConfirmed < 0 ? 0 : Math.max(0, now - lastConfirmed);
        lastConfirmed = now;
        if (state.stableHoldRemaining > 0) {
            state.stableHoldRemaining = Math.max(0, state.stableHoldRemaining - elapsed);
            cleanSince = now; cleanWindows = 0; return;
        }
        state.confirmations++;
        if (cleanSince < 0) cleanSince = now;
        if (now - cleanSince < window) return;
        cleanSince = now;
        state.stableMinimum = state.stableMinimum == 0 ? state.current : Math.min(state.stableMinimum, state.current);
        if (++cleanWindows >= 6) {
            int boundary = state.badDelay == 0 ? 0 : Math.min(MAX, state.badDelay * 3 / 2);
            int reduction = Math.max(1, Math.round(state.current * (boundary > 0 && state.current <= boundary ? .02f : .10f)));
            int candidate = state.current - reduction;
            if (boundary > 0 && state.current > boundary) candidate = Math.max(boundary, candidate);
            state.current = Math.max(minimum, candidate);
            cleanWindows = 0;
        }
    }
    public boolean disconnected(String reason, String pattern, long now) {
        if (lastCommand < 0 || now - lastCommand > Math.max(window, state.current * 2L)
                || !matches(reason, pattern)) return false;
        int triggeringDelay = lastSentPageDelay > 0 ? lastSentPageDelay : state.current;
        state.badDelay = Math.max(state.badDelay, triggeringDelay);
        state.kickCount = Math.min(4, state.kickCount + 1);
        state.stableHoldRemaining = holdSeconds[state.kickCount - 1] * 1000L;
        // Increase by 50% of the pace that triggered the kick; saturate safely.
        state.current = Math.min(MAX, (int) ((triggeringDelay * 3L + 1) / 2));
        state.nextSlot = 0;
        state.safetyMaximum = Math.min(MAX, Math.max(state.safetyMaximum, state.current * 2));
        state.stableMinimum = 0;
        state.lastKickAt = now;
        state.cooldownUntil = now + Math.max(window, state.current * 2L);
        interrupt(); lastCommand = -1;
        return true;
    }
    public static int parseWindow(String value) {
        int seconds = value.isBlank() ? 5 : Integer.parseInt(value.strip());
        if (seconds < 1 || seconds > 300) throw new IllegalArgumentException("Window: 1–300 seconds");
        return seconds;
    }
    public static int parseMinimum(String value) {
        int minimum = value.isBlank() ? MIN : Integer.parseInt(value.strip());
        if (minimum < MIN || minimum > MAX) throw new IllegalArgumentException("Minimum: 240–30000 ms");
        return minimum;
    }
    public static boolean matches(String reason, String pattern) {
        return reason != null && pattern != null && !pattern.isBlank()
                && reason.toLowerCase(Locale.ROOT).contains(pattern.strip().toLowerCase(Locale.ROOT));
    }
}
