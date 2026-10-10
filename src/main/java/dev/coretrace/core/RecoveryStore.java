package dev.coretrace.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** One pending run. Page files are incremental; metadata is replaced only after the page is durable. */
public final class RecoveryStore {
    public record Capture(String directory, String command, long startedAt, int page, int total, Config config) {
        public Capture at(int page, int total) { return new Capture(directory, command, startedAt, page, total, config); }
    }
    public record State(String server, Capture capture, TaskQueue.State queue, String finishSound) {
        public State withCapture(Capture value) { return new State(server, value, queue, finishSound); }
        public State withQueue(TaskQueue.State value) { return new State(server, capture, value, finishSound); }
    }
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path pointer, exports;
    public RecoveryStore(Path pointer, Path exports) {
        this.pointer = pointer; this.exports = exports.toAbsolutePath().normalize();
    }
    public void save(State state) throws IOException {
        if (state == null) { Files.deleteIfExists(pointer); return; }
        Files.createDirectories(pointer.getParent());
        SessionFiles.writeAtomic(pointer, JSON.toJson(state));
    }
    public State load() throws IOException {
        if (!Files.exists(pointer)) return null;
        try {
            State state = JSON.fromJson(Files.readString(pointer), State.class);
            if (state == null || state.server() == null || state.server().isBlank()) throw new IllegalArgumentException("Missing server");
            if (state.capture() != null) {
                Capture c = state.capture();
                if (c.page() < 1 || c.page() > 5000 || c.total() < 0 || (c.total() > 0 && c.total() < c.page()) || c.config() == null
                        || LookupCommand.parse(c.command()).filter(LookupCommand::query).isEmpty())
                    throw new IllegalArgumentException("Invalid capture recovery");
                directory(c); c.config().normalize();
            }
            if (state.queue() != null) {
                var q = state.queue();
                if (q.tasks() == null || q.tasks().isEmpty() || q.index() < 0 || q.index() >= q.tasks().size()
                        || q.phase() == null || q.phase() == TaskQueue.Phase.STOPPED || q.delay() < 0 || q.delay() > 3_600_000)
                    throw new IllegalArgumentException("Invalid queue recovery");
                q.tasks().forEach(TaskDefinition::validate);
                if (state.capture() != null && (q.phase() != TaskQueue.Phase.CAPTURE
                        || !LookupCommand.parse(q.tasks().get(q.index()).command).orElseThrow().command().equals(state.capture().command())))
                    throw new IllegalArgumentException("Capture/queue mismatch");
            } else if (state.capture() == null) throw new IllegalArgumentException("Empty recovery");
            return state;
        } catch (RuntimeException e) { throw new IOException("Invalid recovery file", e); }
    }
    public Path directory(Capture c) throws IOException {
        if (c.directory() == null) throw new IOException("Missing recovery directory");
        Path path = exports.resolve(c.directory()).normalize();
        if (!exports.equals(path.getParent()) || !Files.isDirectory(path)
                || !path.toRealPath().getParent().equals(exports.toRealPath())) throw new IOException("Unsafe recovery directory");
        return path;
    }
    public void writePage(Capture c, CaptureEngine.Page page) throws IOException {
        SessionFiles.writeAtomic(directory(c).resolve(".recovery-page-" + page.number() + ".json"), JSON.toJson(page));
    }
    /** Run on the same I/O executor, after the successful export and its recovery-state transition. */
    public boolean cleanupCompleted(Path exportedDirectory) throws IOException {
        Path dir = exportedDirectory.toAbsolutePath().normalize();
        if (!exports.equals(dir.getParent()) || !dir.toRealPath().getParent().equals(exports.toRealPath()))
            throw new IOException("Unsafe cleanup directory");
        State pending = load();
        if (pending != null && pending.capture() != null
                && pending.capture().directory().equals(dir.getFileName().toString())) return false;
        try (var files = Files.newDirectoryStream(dir)) {
            for (Path file : files) {
                if (file.getFileName().toString().matches("\\.recovery-page-[0-9]+\\.json(?:\\.tmp)?")
                        && Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) Files.delete(file);
            }
        }
        return true;
    }
    public List<CaptureEngine.Page> pages(Capture c) throws IOException {
        if (c.config().smartCsvEnabled()) return List.of();
        var result = new ArrayList<CaptureEngine.Page>();
        Path dir = directory(c);
        int messages = 0;
        for (int n = 1; n < c.page(); n++) {
            try {
                var p = JSON.fromJson(Files.readString(dir.resolve(".recovery-page-" + n + ".json")), CaptureEngine.Page.class);
                if (p == null || p.number() != n || !p.confirmed() || p.messages().size() > 512)
                    throw new IllegalArgumentException("Invalid page");
                messages += p.messages().size();
                if (messages > 100_000) throw new IllegalArgumentException("Too many recovery messages");
                result.add(p);
            } catch (RuntimeException e) { throw new IOException("Invalid saved page " + n, e); }
        }
        return List.copyOf(result);
    }
}
