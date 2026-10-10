package dev.coretrace.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NamedCsvExportTest {
    @TempDir Path temp;

    private static MessageData message(int page) {
        return new MessageData("1.0/h ago - jose: event " + page, List.of(), List.of(), 1000, false);
    }
    private static CaptureEngine.Snapshot snapshot(int completed, boolean pending, Language language) {
        var pages = new ArrayList<CaptureEngine.Page>();
        for (int i = 1; i <= completed; i++) pages.add(new CaptureEngine.Page(i, true, List.of(message(i))));
        return new CaptureEngine.Snapshot("co l a:chat t:1h", "test", 1000, 2000, 1, completed + (pending ? 1 : 0),
                pending ? CaptureEngine.Outcome.TIMEOUT : CaptureEngine.Outcome.COMPLETE, "test", pages,
                pending ? List.of(message(completed + 1)) : List.of(), List.of(), language.code());
    }
    private void export(SessionFiles files, CaptureEngine.Snapshot snapshot) throws Exception {
        files.begin(snapshot.command(), snapshot.server(), snapshot.startedAt());
        files.finish(snapshot);
    }
    @Test void fourPartsUseFrozenNameAndKeepEveryEventWithTheSelectedHeader() throws Exception {
        var config = new Config(); config.csvFileName = "consulta.csv"; config.csvPagesPerFile = 1;
        config.csvColumns = new ArrayList<>(List.of("page_requested", "raw_text"));
        var files = new SessionFiles(temp, Language.SPANISH, config);
        config.csvFileName = "changed"; config.csvPagesPerFile = 0; config.csvColumns.clear();
        export(files, snapshot(4, false, Language.SPANISH));
        assertEquals(temp.resolve("consulta_1.csv"), files.lastCsvPath());
        String summary = Files.readString(temp.resolve("resumen.txt"));
        for (int i = 1; i <= 4; i++) {
            String name = "consulta_" + i + ".csv";
            String csv = Files.readString(temp.resolve(name));
            assertTrue(csv.startsWith("\uFEFFpage_requested,raw_text\r\n"));
            assertTrue(csv.contains(i + ",\"1.0/h ago - jose: event " + i + "\"\r\n"));
            assertEquals(2, csv.lines().count());
            assertTrue(summary.contains(name));
        }
        try (var paths = Files.list(temp)) { assertEquals(4, paths.filter(p -> p.toString().endsWith(".csv")).count()); }
    }
    @Test void persistedNameAcceptsAnExtensionWithoutDuplicatingIt() throws Exception {
        var config = new Config(); config.csvFileName = "Auditoría.CSV";
        config.save(temp.resolve("config.json"));
        Config restored = Config.load(temp.resolve("config.json"));
        assertEquals("Auditoría", restored.csvFileName);
        Path output = temp.resolve("session");
        var files = new SessionFiles(output, Language.ENGLISH, restored);
        var snapshot = snapshot(1, false, Language.ENGLISH);
        export(files, snapshot);
        assertEquals(Report.csv(snapshot), Files.readString(output.resolve("Auditoría.csv")));
        assertEquals(output.resolve("Auditoría.csv"), files.lastCsvPath());
    }
    @Test void pendingPageBeyondBoundaryIsSavedAsAnotherNumberedPart() throws Exception {
        var config = new Config(); config.csvFileName = "partial"; config.csvPagesPerFile = 2;
        export(new SessionFiles(temp, Language.ENGLISH, config), snapshot(2, true, Language.ENGLISH));
        String pending = Files.readString(temp.resolve("partial_2.csv"));
        assertTrue(pending.contains("\r\n3,false,"));
        assertTrue(pending.contains("event 3"));
        assertFalse(Files.readString(temp.resolve("partial_1.csv")).contains("event 3"));
    }
    @Test void rejectsPathsAndWindowsReservedOrInvalidNames() {
        for (String name : List.of("../outside", "..\\outside", "C:\\outside", "con", "NUL.txt", "LPT1", "bad:name",
                "a/b", "a|b", "a?b", "a*b", "a\"b", "a<b", "a>b", "trailing.", ".csv", "a\nb", "a".repeat(101)))
            assertThrows(IllegalArgumentException.class, () -> CsvFileNames.normalize(name), name);
    }
    @Test void emptyNamesKeepTheOriginalLanguageDefaults() throws Exception {
        for (Language language : Language.values()) {
            Path output = temp.resolve(language.code());
            var snapshot = snapshot(1, false, language);
            export(new SessionFiles(output, language, new Config()), snapshot);
            assertEquals(Report.csv(snapshot), Files.readString(output.resolve(SessionFiles.csvName(language))));
        }
    }
    @Test void excelHintOnlyAddsSevenBytesAndPreservesEscapedUnicodeCells() {
        var message = new MessageData("1.0/h ago - José: café, \"hola\"\nsegunda línea", List.of(), List.of(), 1000, false);
        var s = new CaptureEngine.Snapshot("co l a:chat t:1h", "test", 1000, 2000, 1, 1,
                CaptureEngine.Outcome.COMPLETE, "test", List.of(new CaptureEngine.Page(1, true, List.of(message))),
                List.of(), List.of(), Language.SPANISH.code());
        var columns = List.of("server_timestamp", "raw_text", "raw_coordinates");
        String normal = Report.csv(s, columns);
        assertTrue(normal.contains("José"));
        assertTrue(normal.contains("\"\"hola\"\""));
        assertEquals(normal, Report.csv(s, columns, false));
        assertEquals("\uFEFFsep=,\r\n" + normal.substring(1), Report.csv(s, columns, true));
    }
    @Test void excelHintIsFrozenAndWrittenToEverySplitIncludingPendingPage() throws Exception {
        var config = new Config(); config.csvExcelAutoColumns = true; config.csvPagesPerFile = 1;
        config.csvColumns = new ArrayList<>(List.of("raw_text"));
        var files = new SessionFiles(temp, Language.ENGLISH, config);
        config.csvExcelAutoColumns = false;
        export(files, snapshot(2, true, Language.ENGLISH));
        for (int i = 1; i <= 3; i++) {
            String csv = Files.readString(temp.resolve("records_" + i + ".csv"));
            assertTrue(csv.startsWith("\uFEFFsep=,\r\nraw_text\r\n"));
            assertEquals(3, csv.lines().count());
            assertTrue(csv.contains("event " + i));
        }
    }
    @Test void excelSettingPersistsAndTaskCsvOverridesUniversalAndCaptureSettings() throws Exception {
        var config = new Config();
        assertFalse(config.csvExcelAutoColumns);
        config.csvExcelAutoColumns = true;
        var task = new TaskDefinition(); task.command = "/co l a:chat t:1h";
        task.universalSettings = false; task.taskSettings = new Config();
        assertTrue(task.effective(config).csvExcelAutoColumns);
        task.csvSettings = new Config();
        assertFalse(task.effective(config).csvExcelAutoColumns);
        task.csvSettings.csvExcelAutoColumns = true;
        config.tasks.add(task);
        config.saveTaskProfile("Excel");
        config.save(temp.resolve("config.json"));
        var restored = Config.load(temp.resolve("config.json"));
        assertTrue(restored.csvExcelAutoColumns);
        assertTrue(restored.tasks.getFirst().effective(restored).csvExcelAutoColumns);
        restored.tasks.getFirst().csvSettings.csvExcelAutoColumns = false;
        restored.loadTaskProfile(restored.taskProfiles.getFirst());
        assertTrue(restored.tasks.getFirst().effective(restored).csvExcelAutoColumns);
        var files = new SessionFiles(temp.resolve("single"), Language.ENGLISH, restored.tasks.getFirst().effective(restored));
        export(files, snapshot(1, false, Language.ENGLISH));
        assertTrue(Files.readString(files.lastCsvPath()).startsWith("\uFEFFsep=,\r\n"));
    }
}
