package dev.coretrace.core;

import com.google.gson.GsonBuilder;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Confined to one I/O executor; the journal survives cancellation and client crashes. */
public final class SessionFiles implements AutoCloseable {
    private final Path directory;
    private final Language language;
    private final java.util.List<String> csvColumns;
    private final int csvPagesPerFile;
    private final String csvFileName;
    private final boolean csvExcelAutoColumns;
    private Path lastCsvPath;
    private BufferedWriter log, csv;
    private Path csvTemp, csvTarget;
    private String command = "";
    private int firstPage = 1, writtenPages, part, pagesInPart;
    private PageHistory history;
    public PageHistory history(int first) {
        if (history == null) history = new PageHistory(directory, first);
        return history;
    }
    private CaptureEngine.Snapshot batch(CaptureEngine.Page page) {
        return new CaptureEngine.Snapshot(command, "", 0, 0, page.number(), 0,
                CaptureEngine.Outcome.COMPLETE, "", java.util.List.of(page), java.util.List.of(), java.util.List.of(), language.code());
    }
    private void appendPage(CaptureEngine.Page page) throws IOException {
        if (csv == null) {
            part++; pagesInPart = 0;
            csvTarget = directory.resolve(CsvFileNames.fileName(csvFileName, language, csvPagesPerFile > 0 ? part : 0));
            if (lastCsvPath == null) lastCsvPath = csvTarget;
            csvTemp = csvTarget.resolveSibling(csvTarget.getFileName() + ".tmp");
            csv = Files.newBufferedWriter(csvTemp, StandardCharsets.UTF_8);
        }
        String text = Report.csv(batch(page), csvColumns, csvExcelAutoColumns);
        if (pagesInPart > 0) {
            int headerEnd = text.indexOf("\r\n", csvExcelAutoColumns ? text.indexOf("\r\n") + 2 : 0);
            text = text.substring(headerEnd + 2);
        }
        csv.write(text); csv.flush(); pagesInPart++; writtenPages++;
        if (csvPagesPerFile > 0 && pagesInPart == csvPagesPerFile) completePart();
    }
    private void completePart() throws IOException {
        if (csv == null) return;
        csv.close(); csv = null; moveAtomic(csvTemp, csvTarget);
    }
    /** Rebuild only from durable complete pages, never from CSV rows or an interrupted page. */
    public void restart(String query, int first, int resumePage) throws IOException {
        if (csv != null) { csv.close(); csv = null; }
        command = query; firstPage = first; writtenPages = 0; part = 0; pagesInPart = 0; lastCsvPath = null;
        for (int n = first; n < resumePage; n++) appendPage(PageHistory.read(directory, n));
    }
    public SessionFiles(Path directory) { this(directory, Language.SPANISH, new Config()); }
    public SessionFiles(Path directory, Language language) { this(directory, language, new Config()); }
    public SessionFiles(Path directory, Language language, Config config) {
        this.directory = directory; this.language = language;
        this.csvColumns = java.util.List.copyOf(config.csvColumns); this.csvPagesPerFile = config.csvPagesPerFile;
        this.csvFileName = CsvFileNames.normalize(config.csvFileName);
        this.csvExcelAutoColumns = config.csvExcelAutoColumns;
    }
    private String tr(String key, Object... args) { return Translations.text(language, key, args); }
    public static String summaryName(Language language) { return language == Language.ENGLISH ? "summary.txt" : "resumen.txt"; }
    public static String csvName(Language language) { return language == Language.ENGLISH ? "records.csv" : "registros.csv"; }
    public static String jsonName(Language language) { return language == Language.ENGLISH ? "session.json" : "sesion.json"; }
    public void begin(String command, String server, long started) throws IOException {
        this.command = command; this.firstPage = LookupCommand.parse(command).orElseThrow().firstPage();
        Files.createDirectories(directory);
        log = Files.newBufferedWriter(directory.resolve("transcript.log"), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        log.write(tr("log.start", "1.7.1+26.3") + "\n");
        log.write(tr("log.metadata", command, MessageData.clean(server), Instant.ofEpochMilli(started), language.nativeName()) + "\n\n");
        log.flush();
    }
    public void line(int page, MessageData m) throws IOException {
        if (log == null) throw new IOException(tr("log.not_open"));
        log.write("[" + Instant.ofEpochMilli(m.receivedAt()) + "] [" + tr("log.requested_page", page) + "] " + m.text() + "\n");
        if (m.struckThrough()) log.write("    " + tr("log.struck") + "\n");
        for (String hover : m.hovers()) log.write("    [" + tr("log.hover") + "] " + hover.replace("\n", "\n    ") + "\n");
        log.flush();
    }
    public void resume(int page) throws IOException {
        log = Files.newBufferedWriter(directory.resolve("transcript.log"), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        log.write("\n--- " + tr("recovery.log", page) + " ---\n");
        log.flush();
    }
    public void page(CaptureEngine.Page p) throws IOException {
        for (var message : p.messages()) line(p.number(), message);
        writeAtomic(directory.resolve(".recovery-page-" + p.number() + ".json"), new GsonBuilder().create().toJson(p));
        if (p.number() != firstPage + writtenPages) throw new IOException("Nonconsecutive export page");
        appendPage(p);
        if (log != null) {
            log.write("--- " + tr("log.page_closed", p.number(), p.confirmed()) + " ---\n");
            log.flush();
        }
    }
    public void finish(CaptureEngine.Snapshot s) throws IOException {
        try {
            if (s.reportLanguage() != language) throw new IOException("Export language changed during capture");
            command = s.command(); firstPage = s.firstPage();
            if (log != null) {
                for (var message : s.pending()) line(firstPage + s.pages().size(), message);
                for (var message : s.notices()) line(firstPage + s.pages().size(), message);
            }
            for (int i = writtenPages; i < s.pages().size(); i++) appendPage(s.pages().get(i));
            if (!s.pending().isEmpty()) appendPage(new CaptureEngine.Page(firstPage + s.pages().size(), false, s.pending()));
            if (writtenPages == 0) {
                // An empty query still produces a valid CSV header.
                appendPage(new CaptureEngine.Page(firstPage, false, java.util.List.of()));
            }
            completePart();
            boolean single = part == 1;
            if (single && csvPagesPerFile > 0) {
                Path base = directory.resolve(CsvFileNames.fileName(csvFileName, language, 0));
                moveAtomic(lastCsvPath, base); lastCsvPath = base;
            }
            String names;
            if (single) names = lastCsvPath.getFileName().toString();
            else {
                var listed = new java.util.ArrayList<String>();
                for (int n = 1; n <= Math.min(part, 100); n++) listed.add(CsvFileNames.fileName(csvFileName, language, n));
                names = String.join(", ", listed) + (part > 100 ? " … (" + part + ")" : "");
            }
            writeAtomic(directory.resolve(summaryName(language)), Report.summary(s, names));
            Path target = directory.resolve(jsonName(language)), temp = target.resolveSibling(target.getFileName() + ".tmp");
            // Gson streams the lazy page list directly to the writer instead of materializing a giant JSON string.
            try (var writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(s, writer);
            }
            moveAtomic(temp, target);
            if (log != null) { log.write("\nFINAL: " + Report.outcome(s.outcome(), language) + "\n" + s.detail() + "\n"); log.flush(); }
            if (s.outcome().successful()) cleanupCsv(single);
        } finally { close(); }
    }
    private void cleanupCsv(boolean single) throws IOException {
        String base = CsvFileNames.fileName(csvFileName, language, 0);
        String stem = base.substring(0, base.length() - 4);
        var pattern = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(stem) + "(?:_([1-9][0-9]*))?\\.csv(\\.tmp)?");
        try (var files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                var match = pattern.matcher(file.getFileName().toString());
                if (!match.matches() || !Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
                boolean keep = false;
                if (match.group(2) == null) {
                    if (single) keep = match.group(1) == null;
                    else if (match.group(1) != null) {
                        try { keep = Long.parseLong(match.group(1)) <= part; } catch (NumberFormatException ignored) {}
                    }
                }
                if (!keep) Files.delete(file);
            }
        }
    }
    public static void moveAtomic(Path tmp, Path target) throws IOException {
        try { Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    public static void writeAtomic(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        moveAtomic(tmp, target);
    }
    @Override public void close() throws IOException {
        try { if (csv != null) { csv.close(); csv = null; } }
        finally { if (log != null) { log.close(); log = null; } }
    }
    public Path directory() { return directory; }
    public Path lastCsvPath() { return lastCsvPath; }
    /** Exact outputs of the most recent finish, excluding stale parts from an interrupted refresh. */
    public java.util.List<Path> csvPaths() {
        if (lastCsvPath == null) return java.util.List.of();
        if (part == 1) return java.util.List.of(lastCsvPath);
        var paths = new java.util.ArrayList<Path>();
        for (int n = 1; n <= part; n++) paths.add(directory.resolve(CsvFileNames.fileName(csvFileName, language, n)));
        return java.util.List.copyOf(paths);
    }
}
