package dev.coretrace.core;

/** Eligibility and bounded retries for a disconnected capture. */
public final class ResumePolicy {
    public static final int MAX_ATTEMPTS = 5;
    private int attempts;
    public int attempts() { return attempts; }
    public boolean exhausted() { return attempts >= MAX_ATTEMPTS; }
    public boolean attempt() { if (exhausted()) return false; attempts++; return true; }
    public void reset() { attempts = 0; }
    public void stop() { attempts = MAX_ATTEMPTS; }
    public static boolean allows(RecoveryStore.State saved, String currentServer, boolean acrossServers) {
        if (saved == null || currentServer == null || currentServer.isBlank()) return false;
        if (currentServer.equals(saved.server())) return true;
        var capture = saved.capture();
        return acrossServers && capture != null && capture.page() > 1 && capture.config().smartCsvEnabled();
    }
}
