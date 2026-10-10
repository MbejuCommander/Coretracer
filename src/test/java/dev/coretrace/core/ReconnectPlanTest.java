package dev.coretrace.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class ReconnectPlanTest {
    @TempDir Path temp;
    @Test void waitsForEachFailureAndStopsAfterThreeAttempts() {
        var plan = new ReconnectPlan(5000, 3);
        assertFalse(plan.attempt(100000));
        for (int i = 0; i < 3; i++) {
            long now = i * 20000L;
            plan.failed(now);
            assertFalse(plan.attempt(now + 4999));
            assertTrue(plan.attempt(now + 5000));
            assertFalse(plan.attempt(now + 15000));
        }
        plan.failed(60000);
        assertFalse(plan.attempt(100000));
        assertEquals(3, plan.attempts());
    }
    @Test void zeroDelayStillRequiresANewFailureForEveryAttempt() {
        var plan = new ReconnectPlan(0, 2);
        plan.failed(0); assertTrue(plan.attempt(0)); assertFalse(plan.attempt(1));
        plan.failed(2); assertTrue(plan.attempt(2));
        plan.failed(3); assertFalse(plan.attempt(3));
    }
    @Test void hugeDelayDoesNotOverflowAndInvalidLimitsAreRejected() {
        var plan = new ReconnectPlan(Long.MAX_VALUE, 1);
        plan.failed(100); assertFalse(plan.attempt(1000));
        assertThrows(IllegalArgumentException.class, () -> new ReconnectPlan(-1, 3));
        assertThrows(IllegalArgumentException.class, () -> new ReconnectPlan(1, 0));
    }
    @Test void oldSettingsDefaultToOptInAndOptionsPersist() throws Exception {
        var path = temp.resolve("config.json"); Files.writeString(path, "{}");
        var c = Config.load(path);
        assertFalse(c.autoReconnect); assertTrue(c.showReconnectHint);
        assertEquals(5000, c.reconnectDelayMs); assertEquals(3, c.reconnectAttempts);
        c.autoReconnect = true; c.showReconnectHint = false;
        c.reconnectDelayMs = 17; c.reconnectAttempts = 20; c.save(path);
        var saved = Config.load(path);
        assertTrue(saved.autoReconnect); assertFalse(saved.showReconnectHint);
        assertEquals(17, saved.reconnectDelayMs); assertEquals(20, saved.reconnectAttempts);
    }
}
