package dev.coretrace.core;

import java.util.ArrayList;
import java.util.List;

/** One outstanding request at a time; all calls run on the Minecraft client thread. */
public final class CaptureEngine {
    public enum Phase { WAITING, READING, BETWEEN, PAUSED, FINISHED }
    public enum Outcome { COMPLETE, SINGLE_PAGE_INFERRED, EMPTY, CANCELLED, DISCONNECTED, TIMEOUT, LIMIT, MISMATCH, SERVER_ERROR, STORAGE_ERROR }
    public record Settings(int delayMs, int timeoutMs, int settleMs, int maxPages, Language language) {
        public Settings(int delayMs, int timeoutMs, int settleMs, int maxPages) {
            this(delayMs, timeoutMs, settleMs, maxPages, Language.SPANISH);
        }
        public Settings {
            if (language == null) language = Language.SPANISH;
            if (delayMs < 750 || timeoutMs < 5000 || settleMs < 1500 || settleMs >= timeoutMs || maxPages < 1)
                throw new IllegalArgumentException("Invalid capture settings");
        }
    }
    public record Page(int number, boolean confirmed, List<MessageData> messages) {
        public Page { messages = List.copyOf(messages); }
    }
    public record Snapshot(String command, String server, long startedAt, long endedAt, int firstPage,
                           int totalPages, Outcome outcome, String detail, List<Page> pages,
                           List<MessageData> pending, List<MessageData> notices, String language) {
        public Snapshot { pages = List.copyOf(pages); pending = List.copyOf(pending); notices = List.copyOf(notices); }
        public Language reportLanguage() { return Language.fromCode(language); }
    }
    public interface Sink {
        void send(String command);
        void line(int expectedPage, MessageData message);
        void page(Page page);
        void finish(Snapshot snapshot);
    }

    private final LookupCommand command;
    private final Settings settings;
    private final Sink sink;
    private final String server;
    private final long started;
    private final List<Page> pages = new ArrayList<>();
    private final List<MessageData> pending = new ArrayList<>(), notices = new ArrayList<>();
    private Phase phase = Phase.WAITING;
    private Outcome outcome;
    private String detailKey = "status.waiting";
    private Object[] detailArgs = new Object[0];
    private String literalDetail;
    private int expected, total, count;
    private long requestedAt, lastLine, nextAt, pausedAt;
    private boolean paused, hadEntry;
    private Snapshot result;

    public CaptureEngine(LookupCommand command, Settings settings, Sink sink, String server, long now) {
        this.command = command; this.settings = settings; this.sink = sink; this.server = server;
        started = requestedAt = lastLine = now; expected = command.firstPage();
    }

    public boolean accept(MessageData m, long now) {
        if (!active()) return false;
        boolean header = CoreProtectParser.header(m);
        var footer = CoreProtectParser.pagination(m);
        if (CoreProtectParser.prefixed(m)) {
            if (notices.size() < 1000) notices.add(m);
            sink.line(expected, m);
            if (CoreProtectParser.empty(m)) {
                if (pages.isEmpty() && pending.isEmpty()) endKey(Outcome.EMPTY, "reason.empty", now);
                else endKey(Outcome.SERVER_ERROR, "reason.results_stopped", now);
            } else if (CoreProtectParser.failure(m)) end(Outcome.SERVER_ERROR, m.text(), now);
            return true;
        }
        if (header) {
            if (phase == Phase.BETWEEN) {
                endKey(Outcome.MISMATCH, "reason.early_response", now);
                return false;
            }
            if (phase == Phase.READING) {
                endKey(Outcome.MISMATCH, "reason.double_header", now);
                return false;
            }
            phase = Phase.READING; hadEntry = false; setDetail("status.reading", expected);
            append(m, now); return true;
        }
        if (footer.isPresent()) {
            var p = footer.get();
            if (phase == Phase.BETWEEN && p.current() == expected) return true; // repeat footer; do not duplicate a page
            if (phase != Phase.READING) return false;
            if (p.current() != expected || (total > 0 && p.total() != total)) {
                append(m, now);
                endKey(Outcome.MISMATCH, "reason.changed_page", now);
                return true;
            }
            if (!hadEntry) {
                append(m, now);
                endKey(Outcome.MISMATCH, "reason.unrecognized", now);
                return true;
            }
            total = p.total(); append(m, now);
            if (!active()) return true;
            commit(true);
            if (expected >= total) endKey(Outcome.COMPLETE, "reason.complete", now);
            else if (pages.size() >= settings.maxPages()) endKey(Outcome.LIMIT, "reason.page_limit", now);
            else { phase = Phase.BETWEEN; nextAt = now + settings.delayMs(); setDetail("status.page_saved", expected, total); }
            return true;
        }
        if (phase != Phase.READING) return false;
        boolean entry = CoreProtectParser.entry(m);
        if (entry || (hadEntry && CoreProtectParser.coordinates(m)) || m.text().strip().matches("-{3,}")) {
            hadEntry |= entry; append(m, now); return true;
        }
        return false; // Never collect normal player chat or unrelated system messages.
    }

    private void append(MessageData m, long now) {
        pending.add(m); lastLine = now; sink.line(expected, m); count++;
        if (pending.size() > 512 || count >= 100_000) endKey(Outcome.LIMIT, "reason.memory_limit", now);
    }
    private void commit(boolean confirmed) {
        Page page = new Page(expected, confirmed, pending); pages.add(page); pending.clear(); sink.page(page);
    }
    public void tick(long now) {
        if (!active() || paused) return;
        if (phase == Phase.BETWEEN && now >= nextAt) {
            expected++; phase = Phase.WAITING; hadEntry = false; requestedAt = lastLine = now;
            setDetail("status.requesting", expected, total);
            sink.send(command.pageCommand(expected));
        } else if (phase == Phase.READING && total == 0 && hadEntry && now - lastLine >= settings.settleMs()) {
            commit(false);
            endKey(Outcome.SINGLE_PAGE_INFERRED, "reason.inferred", now, settings.settleMs());
        } else if ((phase == Phase.WAITING || phase == Phase.READING) && now - requestedAt >= settings.timeoutMs()) {
            endKey(Outcome.TIMEOUT, "reason.timeout", now);
        }
    }
    public void togglePause(long now) {
        if (!active()) return;
        if (!paused) { paused = true; pausedAt = now; }
        else {
            long delta = now - pausedAt; requestedAt += delta; lastLine = now;
            nextAt = Math.max(nextAt, now + settings.delayMs()); paused = false;
        }
    }
    public void end(Outcome outcome, String why, long now) {
        if (!active()) return;
        literalDetail = why;
        finish(outcome, now);
    }
    public void endKey(Outcome outcome, String key, long now, Object... args) {
        if (!active()) return;
        setDetail(key, args);
        finish(outcome, now);
    }
    private void setDetail(String key, Object... args) { detailKey = key; detailArgs = args.clone(); literalDetail = null; }
    private void finish(Outcome outcome, long now) {
        this.outcome = outcome; phase = Phase.FINISHED;
        result = new Snapshot(command.command(), server, started, now, command.firstPage(), total, outcome,
                detail(settings.language()), pages, pending, notices, settings.language().code());
        sink.finish(result);
    }
    public boolean active() { return phase != Phase.FINISHED; }
    public boolean paused() { return paused && active(); }
    public int completedPages() { return pages.size(); }
    public int totalPages() { return total; }
    public int expectedPage() { return expected; }
    public String detail() { return detail(settings.language()); }
    public String detail(Language language) {
        String value = literalDetail != null ? literalDetail : Translations.text(language, detailKey, detailArgs);
        return paused() ? Translations.text(language, "status.paused", value) : value;
    }
    public Phase phase() { return paused() ? Phase.PAUSED : phase; }
    public Snapshot result() { return result; }
    public List<MessageData> visibleMessages() {
        List<MessageData> result = new ArrayList<>();
        for (Page p : pages) result.addAll(p.messages());
        result.addAll(pending); return List.copyOf(result);
    }
}
