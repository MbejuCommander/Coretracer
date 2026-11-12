package dev.coretrace.client;
import dev.coretrace.core.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ResumeControlTest {
    private static void set(CoreTraceClient client, String name, Object value) throws Exception {
        var f = CoreTraceClient.class.getDeclaredField(name); f.setAccessible(true); f.set(client, value);
    }
    @Test void resumeControlAppearsOnlyForPausedOrRecoverableSimpleCapture() throws Exception {
        var client = new CoreTraceClient(); assertFalse(client.resumable());
        var config = new Config();
        var state = new RecoveryStore.State("saved", new RecoveryStore.Capture("saved", "co l a:sign t:7d", 1, 2, 3, config), null, "");
        set(client, "recovery", state); assertTrue(client.resumable());
        var engine = new CaptureEngine(LookupCommand.parse("co l a:sign t:7d").orElseThrow(), config.settings(), new CaptureEngine.Sink() {
            public void send(String command) {} public void line(int page, MessageData m) {}
            public void page(CaptureEngine.Page p) {} public void finish(CaptureEngine.Snapshot s) {}
        }, "server", System.currentTimeMillis());
        set(client, "engine", engine); assertFalse(client.resumable());
        engine.togglePause(System.currentTimeMillis()); assertTrue(client.resumable());
        client.resumeManually(); assertFalse(engine.paused()); assertFalse(client.resumable());
    }
}
