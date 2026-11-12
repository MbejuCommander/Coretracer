package dev.coretrace.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** Small shareable error report. No query, server address, chat line or record content is included. */
public final class DiagnosticReport {
    private DiagnosticReport() {}
    private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC);

    public record Incident(String version, long timeUtcMs, String category, String code,
                           int savedPages, int announcedPages, int queuePosition, int queueSize,
                           int recoveryAttempts, int spamKickCount, int smartDelayMs,
                           boolean smartCsv, List<String> recentEvents, Throwable exception) {}

    public static boolean reportable(CaptureEngine.Outcome outcome, boolean checkingRecovery, boolean retriesExhausted) {
        return switch (outcome) {
            case MISMATCH, STORAGE_ERROR -> true;
            case TIMEOUT, SERVER_ERROR -> !checkingRecovery || retriesExhausted;
            default -> false;
        };
    }

    public static Path write(Path directory, Incident incident) throws IOException {
        Files.createDirectories(directory);
        String filename = "error-report-" + NAME.format(Instant.ofEpochMilli(incident.timeUtcMs()))
                + "-" + UUID.randomUUID().toString().substring(0, 8) + ".txt";
        Path report = directory.resolve(filename);
        SessionFiles.writeAtomic(report, text(incident));
        return report;
    }

    public static String text(Incident incident) {
        StringBuilder result = new StringBuilder("CORETRACE ERROR REPORT\n");
        result.append("Version: ").append(code(incident.version())).append('\n');
        result.append("Java: ").append(code(System.getProperty("java.version", "unknown"))).append('\n');
        result.append("Time UTC: ").append(Instant.ofEpochMilli(incident.timeUtcMs())).append('\n');
        result.append("Category: ").append(code(incident.category())).append('\n');
        result.append("Code: ").append(code(incident.code())).append('\n');
        result.append("Saved pages: ").append(Math.max(0, incident.savedPages())).append('\n');
        result.append("Announced pages: ").append(Math.max(0, incident.announcedPages())).append('\n');
        result.append("Queue position: ").append(Math.max(0, incident.queuePosition())).append('/')
                .append(Math.max(0, incident.queueSize())).append('\n');
        result.append("Recovery attempts: ").append(Math.max(0, incident.recoveryAttempts())).append('\n');
        result.append("Spam kicks in learning profile: ").append(Math.max(0, incident.spamKickCount())).append('\n');
        result.append("Smart Delay ms: ").append(Math.max(0, incident.smartDelayMs())).append('\n');
        result.append("Smart CSV: ").append(incident.smartCsv()).append('\n');
        result.append("Recent internal events:\n");
        if (incident.recentEvents() != null) for (String event : incident.recentEvents().stream().limit(16).toList())
            result.append("- ").append(code(event)).append('\n');
        if (incident.exception() != null) {
            result.append("Exception: ").append(code(incident.exception().getClass().getName())).append('\n');
            for (StackTraceElement frame : incident.exception().getStackTrace()) {
                if (!frame.getClassName().startsWith("dev.coretrace.")) continue;
                result.append("  at ").append(code(frame.getClassName())).append('.')
                        .append(code(frame.getMethodName())).append(':').append(frame.getLineNumber()).append('\n');
                if (result.length() > 4096) break;
            }
        }
        result.append("\nNo commands, addresses, chat messages or captured records are included.\n");
        return result.toString();
    }

    private static String code(String value) {
        return value == null || !value.matches("[A-Za-z0-9_.+$-]{1,120}") ? "redacted" : value;
    }
}
