package dev.coretrace.core;

import java.util.ArrayList;
import java.util.List;

/** One outstanding request at a time; all calls run on the Minecraft client thread. */
public final class CaptureEngine {
    public enum Phase { WAITING, READING, BETWEEN, PAUSED, FINISHED }
    public enum Outcome { COMPLETE, SINGLE_PAGE_ACCEPTED, SINGLE_PAGE_INFERRED, EMPTY, CANCELLED, DISCONNECTED, TIMEOUT, LIMIT, MISMATCH, SERVER_ERROR, STORAGE_ERROR;
        public boolean successful() { return this == COMPLETE || this == SINGLE_PAGE_ACCEPTED || this == EMPTY; }
    }
    public record Settings(int delayMs, int timeoutMs, int settleMs, int maxPages, Language language, boolean acceptSinglePage, boolean smartCsv) {
        public Settings(int delayMs, int timeoutMs, int settleMs, int maxPages, Language language, boolean acceptSinglePage) {
            this(delayMs, timeoutMs, settleMs, maxPages, language, acceptSinglePage, false);
        }
        public Settings(int delayMs, int timeoutMs, int settleMs, int maxPages, Language language) {
            this(delayMs, timeoutMs, settleMs, maxPages, language, true);
        }
        public Settings(int delayMs, int timeoutMs, int settleMs, int maxPages) {
            this(delayMs, timeoutMs, settleMs, maxPages, Language.SPANISH);
        }
        public Settings {
            if (language == null) language = Language.SPANISH;
            if (delayMs < 750 || timeoutMs < 5000 || settleMs < 1500 || settleMs > timeoutMs - 1000 || maxPages < 1)
                throw new IllegalArgumentException("Invalid capture settings");
        }
    }
    public record Page(int number, boolean confirmed, List<MessageData> messages) {
        public Page { messages = List.copyOf(messages); }
    }
    public record Snapshot(String command, String server, long startedAt, long endedAt, int firstPage,
                           int totalPages, Outcome outcome, String detail, List<Page> pages,
                           List<MessageData> pending, List<MessageData> notices, String language) {
        public Snapshot { pages = PageHistory.freeze(pages); pending = List.copyOf(pending); notices = List.copyOf(notices); }
        public Language reportLanguage() { return Language.fromCode(language); }
    }
    public interface Sink {
        void send(String command);
        void line(int expectedPage, MessageData message);
        void page(Page page);
        void finish(Snapshot snapshot);
        default List<Page> history(int firstPage) { return new ArrayList<>(); }
        default void restartExport() {}
        default void replaceFirst(Page page, int nextPage) {}
        default void replaceFirst(Page page, int nextPage, List<MessageData> newEntries) {
            replaceFirst(page, nextPage);
        }
        default void recovering(int page) {}
        default void recoveringChangedResults(int page) { recovering(page); }
        default void restarted() {}
        default boolean ready() { return true; }
    }

    private final LookupCommand command;
    private final Settings settings;
    private final Sink sink;
    private final String server;
    private final long started;
    private final List<Page> pages;
    private final List<MessageData> pending = new ArrayList<>(), notices = new ArrayList<>();
    private Phase phase = Phase.WAITING;
    private Outcome outcome;
    private String detailKey = "status.waiting";
    private Object[] detailArgs = new Object[0];
    private String literalDetail;
    private int expected, total;
    private long requestedAt, lastLine, nextAt, pausedAt;
    private boolean paused, hadEntry;
    private Snapshot result;
    private boolean recreating, recreationHeader;
    private long recreationReadyAt = -1;
    private SmartCsvOrder smartOrder = new SmartCsvOrder();
    private enum SmartStage { NONE, PREFIX, TAIL }
    private SmartStage smartStage = SmartStage.NONE;
    private final List<MessageData> smartSearch = new ArrayList<>(), smartCurrent = new ArrayList<>();
    private int serverPage = 1, pageOffset, smartSearchPages;
    private boolean smartHeader;
    private boolean inspectRecoveryEnabled;
    private long inspectDelay = 4000, interactionAt = Long.MIN_VALUE, lastRecoveryResponse, zeroAt = -1;
    private int recoveryAttempts, smartShiftAttempts, stablePages;
    private int smartStablePages = 20;
    private int smartShiftRetryLimit = 5;
    private ResumeCountdown inspectionCountdown;

    public void configureInspectionRecovery(boolean enabled, long delay) {
        inspectRecoveryEnabled = enabled; inspectDelay = Math.max(0, delay);
    }
    public void configureSmartStablePages(int pages) {
        if (pages < 5 || pages > 5000) throw new IllegalArgumentException("Stable pages must be between 5 and 5000");
        smartStablePages = pages;
    }
    public void configureSmartShiftRetries(int retries) {
        if (retries < 1 || retries > 10) throw new IllegalArgumentException("Smart shift retries must be between 1 and 10");
        smartShiftRetryLimit = retries;
    }
    public ResumeCountdown inspectionCountdown() { return inspectionCountdown; }
    public long inspectionSeconds(long now) { return inspectionCountdown == null ? 0 : inspectionCountdown.seconds(paused ? pausedAt : now); }
    public String command() { return command.command(); }
    public void blockInteraction(long now) { if (active()) interactionAt = now; }
    public static boolean related(MessageData m) {
        return CoreProtectParser.header(m) || CoreProtectParser.prefixed(m) || CoreProtectParser.entry(m)
                || CoreProtectParser.coordinates(m) || CoreProtectParser.pagination(m).isPresent();
    }
    private void interrupted(long now) { interrupted(now, false); }
    private void interrupted(long now, boolean changedResults) {
        pending.clear(); hadEntry = false;
        boolean retry = command.query() && (changedResults
                ? settings.smartCsv() && ++smartShiftAttempts <= smartShiftRetryLimit
                : inspectRecoveryEnabled && ++recoveryAttempts <= 3);
        if (!retry) {
            if (changedResults) endKey(Outcome.MISMATCH, "smart.changed_order", now, smartShiftRetryLimit, smartStablePages);
            else endKey(Outcome.MISMATCH, "inspect.stopped", now);
            return;
        }
        expected = command.firstPage() + pages.size();
        recreating = false; recreationHeader = false; recreationReadyAt = -1;
        paused = false; phase = Phase.WAITING; stablePages = 0;
        inspectionCountdown = new ResumeCountdown(now, inspectDelay);
        lastRecoveryResponse = now; zeroAt = -1;
        setDetail(changedResults ? "smart.shift_waiting" : "inspect.waiting", expected);
        if (changedResults) sink.recoveringChangedResults(expected);
        else sink.recovering(expected);
    }
    private void restartInspection(long now) {
        inspectionCountdown = null; interactionAt = Long.MIN_VALUE;
        if (settings.smartCsv()) {
            if (!pages.isEmpty()) {
                smartStage = SmartStage.PREFIX;
                smartSearch.clear(); smartCurrent.clear(); smartSearchPages = 0; smartHeader = false;
                serverPage = 1; pageOffset = 0; expected = command.firstPage() + pages.size();
                total = 0;
            } else {
                total = 0; expected = command.firstPage(); smartOrder = new SmartCsvOrder();
            }
        }
        requestedAt = lastLine = now; phase = Phase.WAITING;
        recreating = smartStage == SmartStage.NONE && expected > 1;
        setDetail(recreating ? "recovery.recreating" : "status.reading", expected);
        sink.restarted(); sink.send(command.command());
    }

    public CaptureEngine(LookupCommand command, Settings settings, Sink sink, String server, long now) {
        this(command, settings, sink, server, now, now);
    }
    private CaptureEngine(LookupCommand command, Settings settings, Sink sink, String server, long now, long startedAt) {
        this.command = command; this.settings = settings; this.sink = sink; this.server = server;
        pages = sink.history(command.firstPage());
        started = startedAt; requestedAt = lastLine = now; expected = command.firstPage();
    }
    public static CaptureEngine resume(LookupCommand command, Settings settings, Sink sink, String server,
            long now, long startedAt, int page, int total, List<Page> saved) {
        if (!command.query() || page < 1 || page > 9_999_999) throw new IllegalArgumentException("Invalid recovery query/page");
        CaptureEngine engine = new CaptureEngine(command, settings, sink, server, now, startedAt);
        if (saved.size() != page - 1) throw new IllegalArgumentException("Missing recovery page");
        if (engine.pages instanceof PageHistory history) history.restore(saved);
        else {
            int previous = 0;
            for (Page p : saved) {
                if (p.number() != ++previous || !p.confirmed()) throw new IllegalArgumentException("Incomplete recovery pages");
                engine.pages.add(p);
            }
        }
        engine.expected = page; engine.total = total; engine.recreating = page > 1 && !settings.smartCsv();
        if (settings.smartCsv() && !saved.isEmpty()) engine.smartStage = SmartStage.PREFIX;
        if (engine.recreating) engine.setDetail("recovery.recreating", page);
        return engine;
    }

    public boolean accept(MessageData m, long now) {
        if (!active()) return false;
        if (inspectionCountdown != null) {
            if (related(m)) lastRecoveryResponse = now;
            return related(m);
        }
        if (interactionAt != Long.MIN_VALUE && now - interactionAt <= settings.timeoutMs() && related(m)) {
            interactionAt = Long.MIN_VALUE; interrupted(now); return true;
        }
        if (smartStage != SmartStage.NONE) return acceptSmartSearch(m, now);
        if (recreating) return acceptRecreation(m, now);
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
                interrupted(now);
                return false;
            }
            if (phase == Phase.READING) {
                interrupted(now);
                return false;
            }
            phase = Phase.READING; hadEntry = false; setDetail("status.reading", expected);
            append(m, now); return true;
        }
        if (footer.isPresent()) {
            var p = footer.get();
            if (phase == Phase.BETWEEN && p.current() == expected + pageOffset) return true; // repeat footer
            if (phase != Phase.READING) return false;
            if (p.current() != expected + pageOffset || (total > 0 && p.total() - pageOffset != total)) {
                interrupted(now);
                return true;
            }
            if (!hadEntry) {
                append(m, now);
                endKey(Outcome.MISMATCH, "reason.unrecognized", now);
                return true;
            }
            total = p.total() - pageOffset; append(m, now);
            if (!active()) return true;
            commit(true);
            if (expected >= total) endKey(Outcome.COMPLETE, "reason.complete", now);
            else if (pages.size() >= settings.maxPages()) endKey(Outcome.LIMIT, "reason.page_limit", now);
            else { phase = Phase.BETWEEN; nextAt = now + settings.delayMs(); setDetail("status.page_saved", expected, total); }
            return true;
        }
        if (phase != Phase.READING) return false;
        boolean entry = CoreProtectParser.entry(m);
        if (entry && settings.smartCsv()) {
            String error = smartOrder.accept(EventParser.parse(m, EventParser.query(command.command())).timestamp());
            if ("smart.changed_order".equals(error)) { interrupted(now, true); return true; }
            if (error != null) { append(m, now); endKey(Outcome.MISMATCH, error, now); return true; }
        }
        if (entry || (hadEntry && CoreProtectParser.coordinates(m)) || m.text().strip().matches("-{3,}")) {
            hadEntry |= entry; append(m, now); return true;
        }
        return false; // Never collect normal player chat or unrelated system messages.
    }

    private boolean acceptSmartSearch(MessageData m, long now) {
        if (CoreProtectParser.failure(m) || CoreProtectParser.empty(m)) {
            endKey(Outcome.SERVER_ERROR, "recovery.query_failed", now); return true;
        }
        if (CoreProtectParser.header(m)) {
            smartHeader = true; smartCurrent.clear(); phase = Phase.READING; lastLine = now; return true;
        }
        var footer = CoreProtectParser.pagination(m);
        if (footer.isPresent()) {
            var p = footer.get();
            if (!smartHeader && phase == Phase.BETWEEN) return true;
            if (!smartHeader || p.current() != serverPage) {
                endKey(Outcome.MISMATCH, "smart.join_missing", now); return true;
            }
            smartHeader = false;
            smartSearch.addAll(smartCurrent); smartCurrent.clear(); smartSearchPages++;
            if (smartStage == SmartStage.PREFIX) completeSmartPrefix(p.total(), now);
            else completeSmartTail(p.total(), now);
            return true;
        }
        if (smartHeader && (CoreProtectParser.entry(m) || CoreProtectParser.coordinates(m))) {
            smartCurrent.add(m); lastLine = now; return true;
        }
        return related(m);
    }

    private void completeSmartPrefix(int serverTotal, long now) {
        if (serverTotal < pages.size() - 1) {
            endKey(Outcome.MISMATCH, "smart.join_missing", now); return;
        }
        var query = EventParser.query(command.command());
        List<SmartOverlap.Event> old = SmartOverlap.events(pages.getFirst().messages(), query);
        List<SmartOverlap.Event> fresh = SmartOverlap.events(smartSearch, query);
        int count = Math.min(5, old.size());
        int match = SmartOverlap.find(fresh, old.subList(0, count));
        if (match < 0) {
            if (serverPage >= serverTotal || smartSearchPages >= 12)
                endKey(Outcome.MISMATCH, "smart.join_missing", now);
            else { serverPage++; phase = Phase.BETWEEN; nextAt = now + settings.delayMs(); }
            return;
        }
        List<MessageData> prefix = SmartOverlap.dataOnly(smartSearch.subList(0, fresh.get(match).start()));
        Page original = pages.getFirst();
        var order = new SmartCsvOrder();
        for (MessageData item : prefix) if (CoreProtectParser.entry(item)) {
            if (order.accept(EventParser.parse(item, query).timestamp()) != null) {
                endKey(Outcome.MISMATCH, "smart.join_missing", now); return;
            }
        }
        if (order.accept(EventParser.parse(original.messages().get(old.getFirst().start()), query).timestamp()) != null) {
            endKey(Outcome.MISMATCH, "smart.join_missing", now); return;
        }
        var merged = new ArrayList<MessageData>(original.messages().size() + prefix.size());
        if (!original.messages().isEmpty() && CoreProtectParser.header(original.messages().getFirst()))
            merged.add(original.messages().getFirst());
        merged.addAll(prefix);
        merged.addAll(original.messages().subList(!original.messages().isEmpty()
                && CoreProtectParser.header(original.messages().getFirst()) ? 1 : 0, original.messages().size()));
        if (merged.size() > 512) { endKey(Outcome.MISMATCH, "smart.join_missing", now); return; }
        Page replacement = new Page(original.number(), true, merged);
        pages.set(0, replacement);
        sink.replaceFirst(replacement, command.firstPage() + pages.size(), prefix);
        smartStage = SmartStage.TAIL; smartSearch.clear(); smartCurrent.clear(); smartSearchPages = 0;
        serverPage = Math.max(1, pages.size() - 1);
        phase = Phase.BETWEEN; nextAt = now + settings.delayMs();
    }

    private void completeSmartTail(int serverTotal, long now) {
        var oldMessages = new ArrayList<MessageData>();
        for (int i = Math.max(0, pages.size() - 2); i < pages.size(); i++)
            oldMessages.addAll(SmartOverlap.dataOnly(pages.get(i).messages()));
        var query = EventParser.query(command.command());
        var old = SmartOverlap.events(oldMessages, query);
        var fresh = SmartOverlap.events(smartSearch, query);
        int count = Math.min(5, old.size());
        int match = SmartOverlap.find(fresh, old.subList(old.size() - count, old.size()));
        if (match < 0) {
            if (serverPage >= serverTotal || smartSearchPages >= 14)
                endKey(Outcome.MISMATCH, "smart.join_missing", now);
            else { serverPage++; phase = Phase.BETWEEN; nextAt = now + settings.delayMs(); }
            return;
        }
        var suffix = SmartOverlap.dataOnly(smartSearch.subList(fresh.get(match + count - 1).end(), smartSearch.size()));
        smartOrder = new SmartCsvOrder();
        for (int i = 0; i < old.size(); i++) {
            MessageData item = oldMessages.get(old.get(i).start());
            String error = smartOrder.accept(EventParser.parse(item, EventParser.query(command.command())).timestamp());
            if (error != null) { endKey(Outcome.MISMATCH, "smart.join_missing", now); return; }
        }
        for (MessageData item : suffix) if (CoreProtectParser.entry(item)) {
            String error = smartOrder.accept(EventParser.parse(item, EventParser.query(command.command())).timestamp());
            if (error != null) { endKey(Outcome.MISMATCH, "smart.join_missing", now); return; }
        }
        int logical = command.firstPage() + pages.size();
        pageOffset = serverPage - logical;
        smartStage = SmartStage.NONE; smartSearch.clear(); smartCurrent.clear();
        expected = logical;
        if (!suffix.isEmpty()) {
            total = logical + serverTotal - serverPage;
            pending.addAll(suffix); commit(true);
            if (serverPage >= serverTotal) { endKey(Outcome.COMPLETE, "reason.complete", now); return; }
        } else {
            pageOffset++;
            total = pages.size() + serverTotal - serverPage;
            expected--;
            if (serverPage >= serverTotal) { endKey(Outcome.COMPLETE, "reason.complete", now); return; }
        }
        phase = Phase.BETWEEN; nextAt = now + settings.delayMs();
    }

    private boolean acceptRecreation(MessageData m, long now) {
        // Rebuilding the server lookup produces page 1. None of these messages enter the saved data.
        if (CoreProtectParser.failure(m) || CoreProtectParser.empty(m)) {
            endKey(Outcome.SERVER_ERROR, "recovery.query_failed", now); return true;
        }
        boolean header = CoreProtectParser.header(m);
        var footer = CoreProtectParser.pagination(m);
        boolean related = header || footer.isPresent() || CoreProtectParser.entry(m)
                || CoreProtectParser.coordinates(m) || CoreProtectParser.prefixed(m);
        if (header) { recreationHeader = true; recreationReadyAt = -1; }
        if (footer.isPresent() && recreationHeader) {
            var p = footer.get();
            if (p.current() != 1 || p.total() < expected || (total > 0 && p.total() != total))
                endKey(Outcome.MISMATCH, "recovery.changed", now);
            else { total = p.total(); recreationReadyAt = now + settings.delayMs(); }
        } else if (related && recreationReadyAt >= 0) recreationReadyAt = now + settings.delayMs();
        return related;
    }

    private void append(MessageData m, long now) {
        pending.add(m); lastLine = now; sink.line(expected, m);
        if (pending.size() > 512) endKey(Outcome.LIMIT, "reason.memory_limit", now);
    }
    private void commit(boolean confirmed) {
        Page page = new Page(expected, confirmed, pending); pages.add(page); pending.clear(); sink.page(page);
        if (confirmed) {
            stablePages++;
            if (stablePages >= 5) recoveryAttempts = 0;
            if (stablePages >= smartStablePages) smartShiftAttempts = 0;
        }
    }
    public void tick(long now) {
        if (!active() || paused) return;
        if (inspectionCountdown != null) {
            if (inspectionCountdown.remaining(now) == 0) {
                if (zeroAt < 0) zeroAt = now;
                if (now - zeroAt >= settings.timeoutMs()) { inspectionCountdown = null; endKey(Outcome.MISMATCH, "inspect.no_quiet", now); }
                else if (now - zeroAt >= 250 && now - lastRecoveryResponse >= settings.settleMs()) restartInspection(now);
            }
            return;
        }
        if (smartStage != SmartStage.NONE) {
            if (phase == Phase.BETWEEN && now >= nextAt && sink.ready()) {
                phase = Phase.WAITING; requestedAt = lastLine = now;
                sink.send(command.pageCommand(serverPage));
            } else if ((phase == Phase.WAITING || phase == Phase.READING)
                    && now - requestedAt >= settings.timeoutMs()) endKey(Outcome.TIMEOUT, "smart.join_missing", now);
            return;
        }
        if (recreating) {
            if (recreationReadyAt >= 0 && now >= recreationReadyAt) {
                recreating = false; recreationReadyAt = -1; requestedAt = lastLine = now;
                setDetail("status.requesting", expected, total); sink.send(command.pageCommand(expected));
            } else if (now - requestedAt >= settings.timeoutMs()) endKey(Outcome.TIMEOUT, "recovery.query_failed", now);
            return;
        }
        if (phase == Phase.BETWEEN && now >= nextAt && sink.ready()) {
            expected++; phase = Phase.WAITING; hadEntry = false; requestedAt = lastLine = now;
            setDetail("status.requesting", expected, total);
            sink.send(command.pageCommand(expected + pageOffset));
        } else if (phase == Phase.READING && total == 0 && hadEntry && now - lastLine >= settings.settleMs()) {
            boolean accepted = settings.acceptSinglePage() && expected == 1 && pages.isEmpty();
            commit(false);
            if (accepted) { total = 1; endKey(Outcome.SINGLE_PAGE_ACCEPTED, "reason.single_accepted", now, settings.settleMs()); }
            else endKey(Outcome.SINGLE_PAGE_INFERRED, "reason.inferred", now, settings.settleMs());
        } else if ((phase == Phase.WAITING || phase == Phase.READING) && now - requestedAt >= settings.timeoutMs()) {
            endKey(Outcome.TIMEOUT, "reason.timeout", now);
        }
    }
    public void togglePause(long now) {
        if (!active()) return;
        if (!paused) { paused = true; pausedAt = now; }
        else {
            long delta = now - pausedAt; requestedAt += delta; lastLine = now;
            if (phase == Phase.BETWEEN) nextAt += delta;
            paused = false;
            if (recreationReadyAt >= 0) recreationReadyAt += delta;
            if (inspectionCountdown != null) {
                inspectionCountdown.shift(delta);
                lastRecoveryResponse = now;
                if (zeroAt >= 0) zeroAt += delta;
            }
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
        this.outcome = outcome; phase = Phase.FINISHED; inspectionCountdown = null;
        result = new Snapshot(command.command(), server, started, now, command.firstPage(), total, outcome,
                detail(settings.language()), pages, pending, notices, settings.language().code());
        sink.finish(result);
    }
    public boolean active() { return phase != Phase.FINISHED; }
    public boolean paused() { return paused && active(); }
    public int completedPages() { return pages.size(); }
    public int totalPages() { return total; }
    public int expectedPage() { return expected; }
    public int nextRecoveryPage() {
        int next = command.firstPage() + pages.size();
        return total > 0 ? Math.min(next, total) : next;
    }
    public String detail() { return detail(settings.language()); }
    public String detail(Language language) {
        String value = literalDetail != null ? literalDetail : Translations.text(language, detailKey, detailArgs);
        return paused() ? Translations.text(language, "status.paused", value) : value;
    }
    public Phase phase() { return paused() ? Phase.PAUSED : phase; }
    public Snapshot result() { return result; }
    public List<MessageData> visibleMessages() {
        List<MessageData> result = new ArrayList<>();
        for (int i = Math.max(0, pages.size() - PageHistory.PREVIEW_PAGES); i < pages.size(); i++) result.addAll(pages.get(i).messages());
        result.addAll(pending); return List.copyOf(result);
    }
}
