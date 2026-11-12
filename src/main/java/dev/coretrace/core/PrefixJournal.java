package dev.coretrace.core;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Staged front entries. The manifest is published only after both overlaps match. */
public final class PrefixJournal {
    private static final Gson JSON = new Gson();
    private static final String MANIFEST = ".recovery-prefix.json";
    private PrefixJournal() {}
    @FunctionalInterface public interface Write { void run() throws IOException; }
    private record Chunk(String file, int size) {}
    private record Manifest(List<Chunk> chunks, Integer pageOffset) {}
    public static final class Buffer extends AbstractList<MessageData> {
        private final Path directory;
        private final Consumer<Write> write;
        private final String id = UUID.randomUUID().toString();
        private final List<Chunk> chunks = new ArrayList<>();
        private int size;
        public Buffer(Path directory, Consumer<Write> write) { this.directory = directory; this.write = write; }
        @Override public int size() { return size; }
        @Override public MessageData get(int index) { return view(List.of()).get(index); }
        @Override public boolean addAll(Collection<? extends MessageData> values) {
            if (values.isEmpty()) return false;
            if (values.size() > 512) throw new IllegalArgumentException("Oversized prefix chunk");
            var copy = List.copyOf(values);
            String name = ".recovery-prefix-" + id + "-" + chunks.size() + ".json";
            chunks.add(new Chunk(name, copy.size())); size = Math.addExact(size, copy.size());
            write.accept(() -> SessionFiles.writeAtomic(directory.resolve(name), JSON.toJson(copy)));
            return true;
        }
        public Messages view(List<MessageData> original) {
            var refs = new ArrayList<>(chunks);
            List<MessageData> base = original;
            if (original instanceof Messages previous) {
                if (!directory.equals(previous.directory)) throw new IllegalArgumentException("Different prefix directory");
                refs.addAll(previous.chunks); base = previous.base;
            }
            return new Messages(directory, refs, base);
        }
        @Override public Iterator<MessageData> iterator() { return view(List.of()).iterator(); }
    }
    public static final class Messages extends AbstractList<MessageData> {
        private final Path directory;
        private final List<Chunk> chunks;
        private final List<MessageData> base;
        private final int[] ends;
        private final int prefixSize;
        private int cachedIndex = -1;
        private List<MessageData> cached = List.of();
        private Messages(Path directory, List<Chunk> chunks, List<MessageData> base) {
            this.directory = directory; this.chunks = List.copyOf(chunks); this.base = List.copyOf(base);
            ends = new int[chunks.size()]; int total = 0;
            for (int n = 0; n < chunks.size(); n++) { total = Math.addExact(total, chunks.get(n).size()); ends[n] = total; }
            prefixSize = total;
        }
        @Override public int size() { return Math.addExact(prefixSize, base.size()); }
        @Override public synchronized MessageData get(int index) {
            Objects.checkIndex(index, size());
            if (index >= prefixSize) return base.get(index - prefixSize);
            int chunk = Arrays.binarySearch(ends, index + 1);
            if (chunk < 0) chunk = -chunk - 1;
            if (cachedIndex != chunk) {
                try { cached = readChunk(directory, chunks.get(chunk)); cachedIndex = chunk; }
                catch (IOException e) { throw new UncheckedIOException(e); }
            }
            return cached.get(index - (chunk == 0 ? 0 : ends[chunk - 1]));
        }
        public void publish() throws IOException { publish(0); }
        public void publish(int pageOffset) throws IOException {
            for (Chunk chunk : chunks) readChunk(directory, chunk);
            SessionFiles.writeAtomic(directory.resolve(MANIFEST), JSON.toJson(new Manifest(chunks, pageOffset)));
        }
    }
    private static List<MessageData> readChunk(Path directory, Chunk chunk) throws IOException {
        if (chunk == null || chunk.file() == null || !chunk.file().matches("\\.recovery-prefix-[a-f0-9-]{36}-[0-9]+\\.json")
                || chunk.size() < 1 || chunk.size() > 512) throw new IOException("Invalid prefix chunk reference");
        Path path = directory.resolve(chunk.file());
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing prefix chunk");
        try (var reader = Files.newBufferedReader(path)) {
            MessageData[] data = JSON.fromJson(reader, MessageData[].class);
            if (data == null || data.length != chunk.size()) throw new IOException("Invalid prefix chunk length");
            return List.of(data);
        } catch (RuntimeException e) { throw new IOException("Invalid prefix chunk", e); }
    }
    public static List<MessageData> load(Path directory, List<MessageData> base) throws IOException {
        Path manifest = directory.resolve(MANIFEST);
        if (!Files.exists(manifest)) return base;
        if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe prefix manifest");
        try (var reader = Files.newBufferedReader(manifest)) {
            Manifest state = JSON.fromJson(reader, Manifest.class);
            if (state == null || state.chunks() == null) throw new IOException("Invalid prefix manifest");
            return new Messages(directory, state.chunks(), base);
        } catch (RuntimeException e) { throw new IOException("Invalid prefix manifest", e); }
    }
    public static int savedOffset(Path directory, int fallback) throws IOException {
        Path path = directory.resolve(MANIFEST);
        if (!Files.exists(path)) return fallback;
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unsafe prefix manifest");
        try (var reader = Files.newBufferedReader(path)) {
            Manifest manifest = JSON.fromJson(reader, Manifest.class);
            if (manifest == null || manifest.pageOffset() == null) return fallback;
            int value = manifest.pageOffset();
            if (value < -9_999_999 || value > 9_999_999) throw new IOException("Invalid prefix page offset");
            return value;
        } catch (RuntimeException e) { throw new IOException("Invalid prefix manifest", e); }
    }
    public static void validate(List<MessageData> messages) throws IOException {
        if (messages instanceof Messages saved) for (Chunk chunk : saved.chunks) readChunk(saved.directory, chunk);
    }
    public static List<MessageData> prepend(List<MessageData> prefix, List<MessageData> original) {
        if (prefix instanceof Buffer disk) return disk.view(original);
        var merged = new ArrayList<>(prefix); merged.addAll(original); return List.copyOf(merged);
    }
    /** Each batch ends between complete events, retaining associated coordinates. */
    public static Iterable<List<MessageData>> batches(List<MessageData> messages) {
        return () -> new Iterator<>() {
            int offset;
            public boolean hasNext() { return offset < messages.size(); }
            public List<MessageData> next() {
                if (!hasNext()) throw new NoSuchElementException();
                int end = Math.min(offset + 256, messages.size());
                if (end < messages.size() && CoreProtectParser.coordinates(messages.get(end))) end++;
                var batch = List.copyOf(messages.subList(offset, end)); offset = end; return batch;
            }
        };
    }
    public static void cleanupUnpublished(Path directory) throws IOException {
        var used = new HashSet<String>();
        var loaded = load(directory, List.of());
        if (loaded instanceof Messages messages) for (Chunk chunk : messages.chunks) used.add(chunk.file());
        try (var files = Files.newDirectoryStream(directory, ".recovery-prefix-*.json*")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (name.matches("\\.recovery-prefix-[a-f0-9-]{36}-[0-9]+\\.json(?:\\.tmp)?")
                        && !used.contains(name) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) Files.delete(file);
            }
        }
    }
}
