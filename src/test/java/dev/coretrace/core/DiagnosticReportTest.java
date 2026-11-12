package dev.coretrace.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticReportTest {
    @TempDir Path temp;

    @Test void onlyTerminalFailuresCreateReports() {
        assertFalse(DiagnosticReport.reportable(CaptureEngine.Outcome.DISCONNECTED, true, false));
        assertFalse(DiagnosticReport.reportable(CaptureEngine.Outcome.CANCELLED, false, false));
        assertFalse(DiagnosticReport.reportable(CaptureEngine.Outcome.TIMEOUT, true, false));
        assertTrue(DiagnosticReport.reportable(CaptureEngine.Outcome.TIMEOUT, true, true));
        assertTrue(DiagnosticReport.reportable(CaptureEngine.Outcome.MISMATCH, true, false));
        assertTrue(DiagnosticReport.reportable(CaptureEngine.Outcome.STORAGE_ERROR, false, false));
    }

    @Test void reportKeepsUsefulCodesAndPagesWithoutPrivateMessages() throws Exception {
        var error = new IllegalStateException("/co l u:SecretPlayer on secret.example:25565 C:\\Users\\private\\chat.txt");
        var incident = new DiagnosticReport.Incident("1.7.14+26.3", 1794412423000L,
                "capture", "smart.anchor_passed", 213, 741, 1, 5, 1, 4, 950, true,
                List.of("resume_started.page_214", "SecretPlayer said hello", "capture_finished.page_214"), error);
        Path first = DiagnosticReport.write(temp, incident);
        Path second = DiagnosticReport.write(temp, incident);
        assertNotEquals(first, second);
        String report = Files.readString(first);
        assertTrue(report.contains("Code: smart.anchor_passed"));
        assertTrue(report.contains("Saved pages: 213"));
        assertTrue(report.contains("Announced pages: 741"));
        assertTrue(report.contains("Recovery attempts: 1"));
        assertTrue(report.contains("resume_started.page_214"));
        assertTrue(report.contains("redacted"));
        assertFalse(report.contains("SecretPlayer"));
        assertFalse(report.contains("secret.example"));
        assertFalse(report.contains("chat.txt"));
        assertFalse(report.contains("/co l"));
    }
}
