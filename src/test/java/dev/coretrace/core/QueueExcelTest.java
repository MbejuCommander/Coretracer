package dev.coretrace.core;

import com.google.gson.Gson;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.*;
import static org.junit.jupiter.api.Assertions.*;

class QueueExcelTest {
    @TempDir Path root;
    private QueueExcel book() throws Exception {
        Files.createDirectories(root.resolve("book")); return new QueueExcel(root, "book");
    }
    private Path csv(String file, String body) throws Exception {
        Path target = root.resolve(file); Files.createDirectories(target.getParent()); Files.writeString(target, body); return target;
    }
    private static Document xml(Path file, String entry) throws Exception {
        try (var zip = new ZipFile(file.toFile()); var in = zip.getInputStream(zip.getEntry(entry))) {
            var f = DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return f.newDocumentBuilder().parse(in);
        }
    }
    private static List<String> names(Path file) throws Exception {
        var nodes = xml(file, "xl/workbook.xml").getElementsByTagName("sheet");
        var result = new ArrayList<String>();
        for (int i = 0; i < nodes.getLength(); i++) result.add(((Element)nodes.item(i)).getAttribute("name"));
        return result;
    }
    @Test void disabledByDefaultIncludingOldConfigsAndSettingSurvivesSave() throws Exception {
        assertFalse(new Config().queueExcel); assertFalse(new Gson().fromJson("{}", Config.class).queueExcel);
        var c = new Config(); c.queueExcel = true; c.save(root.resolve("config.json"));
        assertTrue(Config.load(root.resolve("config.json")).queueExcel);
    }
    @Test void combinesPartsWithOneHeaderPreservesCoordinatesQuotesNewlinesAndLiteralFormulas() throws Exception {
        var book = book();
        Path first = csv("one/one_1.csv", "\uFEFFsep=,\r\nobject,x,y,z,content\r\nstone,-37257,-58,75011,\"a,b\n\"\"quoted\"\" & <tag>\"\r\n");
        Path second = csv("one/one_2.csv", "object,x,y,z,content\nwood,2,3,4,=1+1\n");
        book.saveTask(0, "Items", List.of(first, second));
        assertEquals(List.of("Items"), names(book.file()));
        var sheet = xml(book.file(), "xl/worksheets/sheet1.xml");
        assertEquals(3, sheet.getElementsByTagName("row").getLength());
        var cells = sheet.getElementsByTagName("c");
        assertEquals("-37257", cells.item(6).getTextContent());
        assertEquals("n", ((Element)cells.item(6)).getAttribute("t"));
        assertEquals("a,b\n\"quoted\" & <tag>", cells.item(9).getTextContent());
        assertEquals("=1+1", cells.item(14).getTextContent());
        assertEquals("inlineStr", ((Element)cells.item(14)).getAttribute("t"));
        assertEquals(0, sheet.getElementsByTagName("f").getLength());
        assertEquals("A1:E3", ((Element)sheet.getElementsByTagName("autoFilter").item(0)).getAttribute("ref"));
        try (var zip = new ZipFile(book.file().toFile())) {
            for (var entries = zip.entries(); entries.hasMoreElements();) xml(book.file(), entries.nextElement().getName());
        }
    }
    @Test void restartKeepsPreviousTasksAndReplacesCurrentSheetEvenWhenHeadersChange() throws Exception {
        var book = book(); Path first = csv("one/records.csv", "object,x\nstone,1\n");
        book.saveTask(0, "one", List.of(first));
        Path second = csv("two/records.csv", "raw_text\npartial\n"); book.saveTask(1, "two", List.of(second));
        Files.writeString(second, "object,z\nwood,99\n");
        new QueueExcel(root, "book").saveTask(1, "two", List.of(second));
        assertEquals(List.of("one", "two"), names(book.file()));
        assertEquals(2, xml(book.file(), "xl/worksheets/sheet1.xml").getElementsByTagName("row").getLength());
        String text = xml(book.file(), "xl/worksheets/sheet2.xml").getDocumentElement().getTextContent();
        assertTrue(text.contains("wood99")); assertFalse(text.contains("partial"));
    }
    @Test void handlesEmptyTasksDuplicateInvalidNamesAndContinuationSheets() throws Exception {
        Files.createDirectory(root.resolve("book")); var book = new QueueExcel(root, "book", 3);
        Path first = csv("one/r.csv", "raw_text\na\nb\nc\n");
        book.saveTask(0, "'bad:/?*[]name'", List.of(first));
        book.saveTask(1, "BAD_______NAME", List.of(csv("two/r.csv", "raw_text\n")));
        book.saveTask(2, "History", List.of(csv("three/r.csv", "raw_text\n")));
        book.saveTask(3, "a".repeat(60), List.of(csv("four/r.csv", "raw_text\n")));
        var names = names(book.file()); assertEquals(5, names.size());
        assertEquals(names.size(), names.stream().map(s -> s.toLowerCase(Locale.ROOT)).distinct().count());
        for (String name : names) { assertTrue(name.length() <= 31); assertFalse(name.matches(".*[\\\\/?:*\\[\\]].*")); assertFalse(name.startsWith("'")); assertFalse(name.endsWith("'")); }
        assertEquals(3, xml(book.file(), "xl/worksheets/sheet1.xml").getElementsByTagName("row").getLength());
        assertEquals(2, xml(book.file(), "xl/worksheets/sheet2.xml").getElementsByTagName("row").getLength());
        assertEquals(1, xml(book.file(), "xl/worksheets/sheet3.xml").getElementsByTagName("row").getLength());
    }
    @Test void writeFailureRetainsLastWorkbookCsvAndPendingRecovery() throws Exception {
        var book = book(); Path source = csv("run/r.csv", "raw_text\ngood\n"); book.saveTask(0, "one", List.of(source));
        byte[] previous = Files.readAllBytes(book.file());
        var store = new RecoveryStore(root.resolve("recovery.json"), root);
        var task = new TaskDefinition(); task.command = ProgressiveCaptureTest.QUERY;
        var capture = new RecoveryStore.Capture("run", task.command, 1, 1, 3, ProgressiveCaptureTest.normal());
        var queue = new TaskQueue.State(List.of(task), 3000, 0, 0, TaskQueue.Phase.CAPTURE, 0);
        var state = new RecoveryStore.State("server", capture, queue, "", "book");
        store.save(state); store.writePage(capture, ProgressiveCaptureTest.page(1));
        Files.writeString(source, "raw_text\n\"broken");
        assertThrows(IOException.class, () -> book.saveTask(0, "one", List.of(source)));
        assertArrayEquals(previous, Files.readAllBytes(book.file()));
        assertFalse(store.cleanupCompleted(root.resolve("run")));
        assertNotNull(store.load()); assertTrue(Files.exists(source));
        assertTrue(Files.exists(root.resolve("run/.recovery-page-1.json")));
        Files.writeString(source, "raw_text\nrecovered\n");
        new QueueExcel(root, store.load().excelDirectory()).saveTask(0, "one", List.of(source));
        store.save(null); assertTrue(store.cleanupCompleted(root.resolve("run")));
        assertFalse(Files.exists(root.resolve("run/.recovery-page-1.json")));
        assertTrue(Files.exists(book.file())); assertTrue(Files.exists(source));
    }
    @Test void cancelAndSaveIncludesPartialTaskAndOnlyThenCanRecoveryBeCleared() throws Exception {
        var book = book();
        book.saveTask(0, "completed", List.of(csv("one/r.csv", "raw_text\nfirst\n")));
        var config = ProgressiveCaptureTest.normal(); config.csvPagesPerFile = 1;
        var store = new RecoveryStore(root.resolve("recovery.json"), root);
        Files.createDirectories(root.resolve("two"));
        var capture = new RecoveryStore.Capture("two", ProgressiveCaptureTest.QUERY, 1, 1, 3, config);
        var task = new TaskDefinition(); task.command = capture.command();
        store.save(new RecoveryStore.State("server", capture,
                new TaskQueue.State(List.of(task, task.copy()), 3000, 1, 0, TaskQueue.Phase.CAPTURE, 0), "", "book"));
        try (var files = new SessionFiles(root.resolve("two"), Language.ENGLISH, config)) {
            files.begin(capture.command(), "server", 1); files.page(ProgressiveCaptureTest.page(1));
            files.finish(ProgressiveCaptureTest.snapshot(List.of(ProgressiveCaptureTest.page(1)),
                    List.of(MessageData.plain("1/h ago - jose: unfinished", 2)), CaptureEngine.Outcome.CANCELLED));
            assertFalse(store.cleanupCompleted(files.directory()));
            book.saveTask(1, "partial", files.csvPaths());
            assertEquals(List.of("completed", "partial"), names(book.file()));
            assertEquals(3, xml(book.file(), "xl/worksheets/sheet2.xml").getElementsByTagName("row").getLength());
            store.save(null); assertTrue(store.cleanupCompleted(files.directory()));
        }
    }
    @Test void excelRecoveryIdentifierSurvivesEveryTransitionAndLegacyStateRemainsValid() throws Exception {
        book(); var task = new TaskDefinition(); task.command = ProgressiveCaptureTest.QUERY;
        task.commands.add(new TaskCommand("/test", 100));
        var store = new RecoveryStore(root.resolve("recovery.json"), root);
        var q = new TaskQueue.State(List.of(task), 3000, 0, 0, TaskQueue.Phase.CUSTOM, 80);
        var state = new RecoveryStore.State("server", null, q, "sound", "book");
        store.save(state.withCapture(null).withQueue(q));
        assertEquals("book", store.load().excelDirectory());
        assertEquals(TaskQueue.Phase.CUSTOM, store.load().queue().phase());
        store.save(new RecoveryStore.State("server", null, q, "")); assertNull(store.load().excelDirectory());
        store.save(new RecoveryStore.State("server", null, q, "", "..")); assertThrows(IOException.class, store::load);
    }
    @Test void rejectsOversizeCellsWithoutPublishingTruncation() throws Exception {
        var book = book(); var source = csv("one/r.csv", "raw_text\n" + "x".repeat(32768));
        assertThrows(IOException.class, () -> book.saveTask(0, "long", List.of(source)));
        assertFalse(Files.exists(book.file())); assertTrue(Files.exists(source));
    }
    @Test void truncationCannotLeaveAnApostropheAtTheEndOfASheetName() throws Exception {
        var book = book(); book.saveTask(0, "a".repeat(30) + "'suffix", List.of(csv("one/r.csv", "raw_text\n")));
        assertEquals("a".repeat(30), names(book.file()).getFirst());
    }
    @Test void missingPreviousCsvDoesNotReplaceTheExistingWorkbook() throws Exception {
        var book = book(); Path first = csv("one/r.csv", "raw_text\nfirst\n");
        book.saveTask(0, "one", List.of(first)); byte[] previous = Files.readAllBytes(book.file()); Files.delete(first);
        assertThrows(IOException.class, () -> book.saveTask(1, "two", List.of(csv("two/r.csv", "raw_text\nsecond\n"))));
        assertArrayEquals(previous, Files.readAllBytes(book.file()));
    }
}
