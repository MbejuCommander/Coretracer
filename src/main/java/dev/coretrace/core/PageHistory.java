package dev.coretrace.core;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Disk-backed consecutive pages. Only the recent preview stays in memory. Reads run on the I/O worker. */
public final class PageHistory extends AbstractList<CaptureEngine.Page> {
    public static final int PREVIEW_PAGES = 16;
    private static final Gson JSON = new Gson();
    private final Path directory;
    private final int first;
    private int count;
    private final Map<Integer, CaptureEngine.Page> recent = new LinkedHashMap<>();

    public PageHistory(Path directory, int first) { this.directory = directory; this.first = first; }
    @Override public synchronized int size() { return count; }
    @Override public synchronized CaptureEngine.Page get(int index) {
        Objects.checkIndex(index, count);
        var cached = recent.get(index);
        if (cached != null) return cached;
        try { return read(directory, first + index); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }
    @Override public synchronized boolean add(CaptureEngine.Page page) {
        if (page.number() != first + count) throw new IllegalArgumentException("Nonconsecutive page");
        recent.put(count++, page);
        while (recent.size() > PREVIEW_PAGES) recent.remove(recent.keySet().iterator().next());
        return true;
    }
    @Override public synchronized void clear() { count = 0; recent.clear(); }
    public synchronized List<CaptureEngine.Page> freeze() { return new View(directory, first, count, Map.copyOf(recent)); }
    public synchronized void restore(List<CaptureEngine.Page> saved) {
        clear();
        if (saved instanceof View view && view.directory.equals(directory) && view.first == first) {
            count = view.count; recent.putAll(view.recent);
        } else for (var page : saved) add(page);
    }
    public static List<CaptureEngine.Page> freeze(List<CaptureEngine.Page> pages) {
        if (pages instanceof PageHistory history) return history.freeze();
        return pages instanceof View ? pages : List.copyOf(pages);
    }
    public static CaptureEngine.Page read(Path directory, int number) throws IOException {
        try (var reader = Files.newBufferedReader(directory.resolve(".recovery-page-" + number + ".json"))) {
            var page = JSON.fromJson(reader, CaptureEngine.Page.class);
            if (page == null || page.number() != number || page.messages().size() > 513)
                throw new IOException("Invalid saved page " + number);
            return page;
        } catch (RuntimeException e) { throw new IOException("Invalid saved page " + number, e); }
    }
    public static List<CaptureEngine.Page> load(Path directory, int first, int count) throws IOException {
        var history = new PageHistory(directory, first);
        for (int n = first; n < first + count; n++) {
            var page = read(directory, n);
            if (!page.confirmed() || page.messages().size() > 512) throw new IOException("Unconfirmed saved page " + n);
            history.add(page);
        }
        return history.freeze();
    }
    private static final class View extends AbstractList<CaptureEngine.Page> {
        final Path directory;
        final int first, count;
        final Map<Integer, CaptureEngine.Page> recent;
        View(Path directory, int first, int count, Map<Integer, CaptureEngine.Page> recent) {
            this.directory = directory; this.first = first; this.count = count; this.recent = recent;
        }
        @Override public int size() { return count; }
        @Override public CaptureEngine.Page get(int index) {
            Objects.checkIndex(index, count);
            var cached = recent.get(index);
            if (cached != null) return cached;
            try { return read(directory, first + index); }
            catch (IOException e) { throw new UncheckedIOException(e); }
        }
    }
}
