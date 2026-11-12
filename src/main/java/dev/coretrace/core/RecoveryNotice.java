package dev.coretrace.core;
/** One delayed explanatory notice for the current recovery event. */
public final class RecoveryNotice {
    private long due = -1;
    public void schedule(long now) { due = now + 2000; }
    public void cancel() { due = -1; }
    public boolean poll(long now) {
        if (due < 0 || now < due) return false;
        due = -1; return true;
    }
}
