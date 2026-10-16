package dev.coretrace.core;

import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Durable queue manifest plus a streaming XLSX projection of the existing CSV exports.
 * Confined to the capture I/O executor. Never owns or deletes capture/recovery files. */
public final class QueueExcel {
    private static final Gson JSON = new Gson();
    private static final String MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final Set<String> NUMBERS = Set.of("page_requested", "amount", "x", "y", "z");
    private final Path exports, directory;
    private final String fileName;
    private final int rowLimit;
    private record Task(int index, String name, List<String> csvFiles) {}
    private record Manifest(List<Task> tasks) {}
    public QueueExcel(Path exports, String directory) throws IOException { this(exports, directory, ""); }
    public QueueExcel(Path exports, String directory, String name) throws IOException {
        this(exports, directory, name, 1_048_576);
    }
    QueueExcel(Path exports, String directory, int rowLimit) throws IOException {
        this(exports, directory, "", rowLimit);
    }
    QueueExcel(Path exports, String directory, String name, int rowLimit) throws IOException {
        this.exports = exports.toAbsolutePath().normalize();
        this.directory = safeDirectory(this.exports, directory);
        String stem = normalizeName(name);
        this.fileName = (stem.isEmpty() ? "tasks" : stem) + ".xlsx";
        if (rowLimit < 2 || rowLimit > 1_048_576) throw new IllegalArgumentException("Invalid sheet row limit");
        this.rowLimit = rowLimit;
    }
    /** The typed name is a file stem, never a path. An optional .xlsx suffix is accepted. */
    public static String normalizeName(String value) {
        if (value == null || value.isBlank()) return "";
        String stem = value.strip();
        if (stem.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            stem = stem.substring(0, stem.length() - 5);
            if (stem.isBlank()) throw new IllegalArgumentException("Invalid Excel filename");
        }
        return CsvFileNames.normalize(stem);
    }
    public static Path safeDirectory(Path root, String name) throws IOException {
        Path base = root.toAbsolutePath().normalize();
        if (name == null || name.isBlank()) throw new IOException("Missing workbook directory");
        Path path = base.resolve(name).normalize();
        if (!base.equals(path.getParent()) || !Files.isDirectory(path)
                || !path.toRealPath().getParent().equals(base.toRealPath())) throw new IOException("Unsafe workbook directory");
        return path;
    }
    public String directoryName() { return directory.getFileName().toString(); }
    public String fileName() { return fileName; }
    public Path file() { return directory.resolve(fileName); }
    /** Replace the current task, including partial output, so resumption cannot duplicate sheets. */
    public void saveTask(int index, String name, List<Path> csvFiles) throws IOException {
        if (index < 0 || csvFiles.isEmpty()) throw new IOException("Empty workbook task");
        var tasks = new ArrayList<Task>();
        Path manifest = directory.resolve("queue-excel.json");
        if (Files.exists(manifest)) {
            try {
                var saved = JSON.fromJson(Files.readString(manifest), Manifest.class);
                if (saved == null || saved.tasks() == null) throw new IllegalArgumentException("Missing tasks");
                tasks.addAll(saved.tasks());
            } catch (RuntimeException e) { throw new IOException("Invalid Excel manifest", e); }
        }
        var paths = new ArrayList<String>();
        for (Path path : csvFiles) {
            String relative = exports.relativize(path.toAbsolutePath().normalize()).toString();
            checkedCsv(relative); paths.add(relative);
        }
        tasks.removeIf(t -> t.index() == index);
        tasks.add(new Task(index, name == null ? "" : name, List.copyOf(paths)));
        tasks.sort(Comparator.comparingInt(Task::index));
        // The manifest is durable before workbook publication; a failed write retains its inputs.
        SessionFiles.writeAtomic(manifest, JSON.toJson(new Manifest(tasks)));
        Path temp = directory.resolve(fileName + ".tmp");
        try {
            write(temp, tasks);
            SessionFiles.moveAtomic(temp, file());
        } catch (IOException | RuntimeException e) {
            // Do not mask the original error or touch the last good workbook.
            try { Files.deleteIfExists(temp); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }
    /** Remove completed queue inputs only after the final workbook and queue checkpoint exist.
     * Reject directories containing files that CoreTrace did not generate. */
    public void cleanupSources() throws IOException {
        if (!Files.isRegularFile(file(), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Workbook is not complete");
        Path manifest = directory.resolve("queue-excel.json");
        if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Missing Excel manifest");
        Manifest saved;
        try { saved = JSON.fromJson(Files.readString(manifest), Manifest.class); }
        catch (RuntimeException e) { throw new IOException("Invalid Excel manifest", e); }
        if (saved == null || saved.tasks() == null || saved.tasks().isEmpty())
            throw new IOException("Empty Excel manifest");
        Set<Path> sources = new LinkedHashSet<>();
        Set<Path> csvInputs = new HashSet<>();
        for (Task task : saved.tasks()) {
            if (task == null || task.csvFiles() == null || task.csvFiles().isEmpty())
                throw new IOException("Invalid Excel task manifest");
            for (String input : task.csvFiles()) {
                Path csv = checkedCsv(input);
                csvInputs.add(csv);
                Path source = csv.getParent();
                if (source.equals(directory) || !source.toRealPath().getParent().equals(exports.toRealPath()))
                    throw new IOException("Unsafe Excel source directory");
                sources.add(source);
            }
        }
        for (Path source : sources) try (var files = Files.newDirectoryStream(source)) {
            for (Path item : files) {
                String name = item.getFileName().toString();
                if (!Files.isRegularFile(item, LinkOption.NOFOLLOW_LINKS)
                        || !(csvInputs.contains(item) || name.equals("transcript.log")
                        || name.equals("session.json") || name.equals("sesion.json")
                        || name.equals("summary.txt") || name.equals("resumen.txt")
                        || name.matches("\\.recovery-page-[0-9]+\\.json(?:\\.tmp)?")))
                    throw new IOException("Unexpected file in Excel source directory: " + item);
            }
        }
        for (Path source : sources) {
            try (var files = Files.newDirectoryStream(source)) {
                for (Path item : files) Files.delete(item);
            }
            Files.delete(source);
        }
        Files.delete(manifest);
    }
    private Path checkedCsv(String relative) throws IOException {
        Path path = exports.resolve(relative).normalize();
        if (!path.startsWith(exports) || path.getNameCount() != exports.getNameCount() + 2
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !path.toRealPath().startsWith(exports.toRealPath()) || !path.toString().endsWith(".csv"))
            throw new IOException("Invalid workbook CSV input: " + relative);
        return path;
    }
    private void write(Path target, List<Task> tasks) throws IOException {
        try (var zip = new ZipOutputStream(Files.newOutputStream(target), StandardCharsets.UTF_8)) {
            var out = new BufferedWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8));
            var names = new ArrayList<String>();
            for (Task task : tasks) {
                List<String> header = null;
                int row = 0, part = 0;
                for (String input : task.csvFiles()) try (var csv = new Csv(checkedCsv(input))) {
                    var columns = csv.next();
                    if (header == null) header = columns;
                    if (columns == null || columns.isEmpty() || !columns.equals(header)
                            || columns.stream().anyMatch(c -> !Report.CSV_COLUMNS.contains(c)))
                        throw new IOException("CSV header mismatch");
                    if (row == 0) { startSheet(zip, out, names, task, ++part); writeRow(out, 1, header, header); row = 1; }
                    List<String> values;
                    while ((values = csv.next()) != null) {
                        if (values.size() != header.size()) throw new IOException("CSV column count mismatch");
                        if (row == rowLimit) {
                            endSheet(zip, out, row, header.size());
                            startSheet(zip, out, names, task, ++part); writeRow(out, 1, header, header); row = 1;
                        }
                        writeRow(out, ++row, header, values);
                    }
                }
                if (row > 0) endSheet(zip, out, row, header.size());
            }
            StringBuilder workbook = new StringBuilder("<workbook xmlns=\"" + MAIN + "\" xmlns:r=\"" + REL + "\"><sheets>");
            StringBuilder rels = new StringBuilder("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            StringBuilder types = new StringBuilder("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
            for (int i = 1; i <= names.size(); i++) {
                workbook.append("<sheet name=\"").append(xml(names.get(i - 1))).append("\" sheetId=\"").append(i).append("\" r:id=\"rId").append(i).append("\"/>");
                rels.append("<Relationship Id=\"rId").append(i).append("\" Type=\"").append(REL).append("/worksheet\" Target=\"worksheets/sheet").append(i).append(".xml\"/>");
                types.append("<Override PartName=\"/xl/worksheets/sheet").append(i).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
            }
            entry(zip, out, "xl/workbook.xml", workbook.append("</sheets></workbook>").toString());
            entry(zip, out, "xl/_rels/workbook.xml.rels", rels.append("</Relationships>").toString());
            entry(zip, out, "[Content_Types].xml", types.append("</Types>").toString());
            entry(zip, out, "_rels/.rels", "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"" + REL + "/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
        }
    }
    private static void startSheet(ZipOutputStream zip, Writer out, List<String> names, Task task, int part) throws IOException {
        String base = task.name().isBlank() ? "Task " + (task.index() + 1) : task.name();
        base = base.replaceAll("[\\\\/?:*\\[\\]\\p{Cntrl}]", "_").replaceAll("^'+|'+$", "");
        if (base.isBlank()) base = "Task " + (task.index() + 1);
        String suffix = part == 1 ? "" : " (" + part + ")";
        String name = sheetName(base, suffix);
        int duplicate = 1;
        while (containsName(names, name) || name.equalsIgnoreCase("History")) {
            suffix = " (" + ++duplicate + ")";
            name = sheetName(base, suffix);
        }
        names.add(name);
        zip.putNextEntry(new ZipEntry("xl/worksheets/sheet" + names.size() + ".xml"));
        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"" + MAIN + "\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><sheetData>");
    }
    private static String sheetName(String base, String suffix) {
        String name = truncate(base, 31 - suffix.length()) + suffix;
        return name.replaceAll("^'+|'+$", "");
    }
    private static boolean containsName(List<String> names, String name) { return names.stream().anyMatch(n -> n.equalsIgnoreCase(name)); }
    private static String truncate(String text, int length) {
        int end = Math.min(text.length(), length);
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }
    private static String column(int index) { return String.valueOf((char) ('A' + index)); }
    private static void endSheet(ZipOutputStream zip, Writer out, int rows, int columns) throws IOException {
        out.write("</sheetData><autoFilter ref=\"A1:" + column(columns - 1) + rows + "\"/></worksheet>");
        out.flush(); zip.closeEntry();
    }
    private static void writeRow(Writer out, int row, List<String> header, List<String> values) throws IOException {
        out.write("<row r=\"" + row + "\">");
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            if (value.length() > 32767) throw new IOException("Excel cell exceeds 32767 characters at row " + row);
            out.write("<c r=\"" + column(i) + row + "\"");
            if (row > 1 && NUMBERS.contains(header.get(i)) && value.matches("-?[0-9]{1,15}"))
                out.write(" t=\"n\"><v>" + value + "</v></c>");
            else out.write(" t=\"inlineStr\"><is><t xml:space=\"preserve\">" + xml(value) + "</t></is></c>");
        }
        out.write("</row>");
    }
    private static String xml(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '_' && i + 6 < value.length() && value.substring(i, i + 7).matches("_x[0-9a-fA-F]{4}_")) out.append("_x005F_");
            else if ((c < 32 && c != '\n' && c != '\t') || c == 0xFFFE || c == 0xFFFF) out.append(String.format(Locale.ROOT, "_x%04X_", (int)c));
            else switch (c) {
                case '&' -> out.append("&amp;"); case '<' -> out.append("&lt;"); case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;"); default -> out.append(c);
            }
        }
        return out.toString();
    }
    private static void entry(ZipOutputStream zip, Writer out, String name, String xml) throws IOException {
        zip.putNextEntry(new ZipEntry(name)); out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        out.write(xml); out.flush(); zip.closeEntry();
    }
    /** RFC-style records, including quoted commas, escaped quotes and embedded newlines. */
    private static final class Csv implements AutoCloseable {
        private final PushbackReader in;
        Csv(Path path) throws IOException {
            in = new PushbackReader(Files.newBufferedReader(path, StandardCharsets.UTF_8), 8);
            int first = in.read(); if (first != 0xFEFF && first != -1) in.unread(first);
            char[] prefix = new char[7]; int count = 0, ch;
            while (count < prefix.length && (ch = in.read()) != -1) prefix[count++] = (char)ch;
            String hint = new String(prefix, 0, count);
            if (!hint.equals("sep=,\r\n")) in.unread(prefix, 0, count);
        }
        List<String> next() throws IOException {
            var fields = new ArrayList<String>(); var field = new StringBuilder();
            boolean quoted = false, closed = false, any = false;
            int c;
            while ((c = in.read()) != -1) {
                any = true;
                if (quoted) {
                    if (c == '"') {
                        int next = in.read();
                        if (next == '"') field.append('"');
                        else { quoted = false; closed = true; if (next != -1) in.unread(next); }
                    } else field.append((char)c);
                } else if (c == ',' || c == '\r' || c == '\n') {
                    fields.add(field.toString()); field.setLength(0); closed = false;
                    if (c != ',') {
                        if (c == '\r') { int next = in.read(); if (next != '\n' && next != -1) in.unread(next); }
                        return fields;
                    }
                } else if (c == '"' && field.isEmpty() && !closed) quoted = true;
                else { if (closed || c == '"') throw new IOException("Invalid CSV quoting"); field.append((char)c); }
                if (field.length() > 32767) throw new IOException("Excel cell exceeds 32767 characters");
            }
            if (quoted) throw new IOException("Unclosed CSV field");
            if (!any) return null;
            fields.add(field.toString()); return fields;
        }
        public void close() throws IOException { in.close(); }
    }
}
