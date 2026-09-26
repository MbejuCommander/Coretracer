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
    private Path lastCsvPath;
    private BufferedWriter log;
    public SessionFiles(Path directory) { this(directory, Language.SPANISH, new Config()); }
    public SessionFiles(Path directory, Language language) { this(directory, language, new Config()); }
    public SessionFiles(Path directory, Language language, Config config) {
        this.directory = directory; this.language = language;
        this.csvColumns = java.util.List.copyOf(config.csvColumns); this.csvPagesPerFile = config.csvPagesPerFile;
        this.csvFileName = CsvFileNames.normalize(config.csvFileName);
    }
    private String tr(String key, Object... args) { return Translations.text(language, key, args); }
    public static String summaryName(Language language) { return language == Language.ENGLISH ? "summary.txt" : "resumen.txt"; }
    public static String csvName(Language language) { return language == Language.ENGLISH ? "records.csv" : "registros.csv"; }
    public static String jsonName(Language language) { return language == Language.ENGLISH ? "session.json" : "sesion.json"; }
    public void begin(String command, String server, long started) throws IOException {
        Files.createDirectories(directory);
        log = Files.newBufferedWriter(directory.resolve("transcript.log"), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        log.write(tr("log.start", "1.2.1+26.3") + "\n");
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
    public void page(CaptureEngine.Page p) throws IOException {
        if (log != null) {
            log.write("--- " + tr("log.page_closed", p.number(), p.confirmed()) + " ---\n");
            log.flush();
        }
    }
    public void finish(CaptureEngine.Snapshot s) throws IOException {
        try {
            if (s.reportLanguage() != language) throw new IOException("Export language changed during capture");
            var csvNames = new java.util.ArrayList<String>();
            int chunkSize = csvPagesPerFile;
            var exportPages = new java.util.ArrayList<>(s.pages());
            if (!s.pending().isEmpty()) {
                int pendingPage = s.pages().isEmpty() ? s.firstPage() : s.pages().getLast().number() + 1;
                exportPages.add(new CaptureEngine.Page(pendingPage, false, s.pending()));
            }
            if (chunkSize <= 0 || exportPages.size() <= chunkSize) {
                String name = CsvFileNames.fileName(csvFileName, language, 0);
                csvNames.add(name);
                lastCsvPath = directory.resolve(name);
                writeAtomic(lastCsvPath, Report.csv(s, csvColumns));
            }
            else {
                int chunk = 0;
                for (int start = 0; start < exportPages.size(); start += chunkSize) {
                    int end = Math.min(start + chunkSize, exportPages.size());
                    var part = new CaptureEngine.Snapshot(s.command(), s.server(), s.startedAt(), s.endedAt(), s.firstPage(),
                            s.totalPages(), s.outcome(), s.detail(), exportPages.subList(start, end), java.util.List.of(),
                            s.notices(), s.language());
                    String name = CsvFileNames.fileName(csvFileName, language, ++chunk);
                    csvNames.add(name);
                    Path csvPath = directory.resolve(name);
                    if (lastCsvPath == null) lastCsvPath = csvPath;
                    writeAtomic(csvPath, Report.csv(part, csvColumns));
                }
            }
            writeAtomic(directory.resolve(summaryName(language)), Report.summary(s, String.join(", ", csvNames)));
            writeAtomic(directory.resolve(jsonName(language)), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(s));
            if (log != null) {
                log.write("\nFINAL: " + Report.outcome(s.outcome(), language) + "\n" + s.detail() + "\n");
                log.flush();
            }
        } finally { close(); }
    }
    public static void writeAtomic(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try { Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    @Override public void close() throws IOException { if (log != null) { log.close(); log = null; } }
    public Path directory() { return directory; }
    public Path lastCsvPath() { return lastCsvPath; }
}
