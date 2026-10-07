package dev.coretrace.core;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class TaskSettingsTest {
    @TempDir Path temp;
    @Test void independentCaptureAndCsvInheritanceWithSilentTaskDefaults() {
        Config global = new Config(); global.csvFileName = "global"; global.csvPagesPerFile = 3;
        TaskDefinition t = new TaskDefinition(); t.csvName = "Simply1.csv";
        Config effective = t.effective(global);
        assertEquals("Simply1", effective.csvFileName); assertEquals(3, effective.csvPagesPerFile);
        assertEquals(global.delayMs, effective.delayMs); assertEquals("", effective.startSound); assertEquals("", effective.finishSound);
        t.universalSettings = false; t.taskSettings = new Config(); t.taskSettings.delayMs = 2300; t.taskSettings.csvPagesPerFile = 5;
        effective = t.effective(global); assertEquals(2300, effective.delayMs); assertEquals(3, effective.csvPagesPerFile);
        t.csvSettings = new Config(); t.csvSettings.csvPagesPerFile = 7; t.csvSettings.csvColumns = new ArrayList<>(List.of("raw_text"));
        effective = t.effective(global); assertEquals(7, effective.csvPagesPerFile); assertEquals(List.of("raw_text"), effective.csvColumns);
        effective.csvColumns.clear(); assertEquals(List.of("raw_text"), t.csvSettings.csvColumns);
        assertEquals("global", global.csvFileName); assertEquals(3, global.csvPagesPerFile);
    }
    @Test void queueAndOverridesSurviveRestartAndSnapshotsAreIndependent() throws Exception {
        Config global = new Config(); TaskDefinition t = new TaskDefinition(); t.command = "/co l a:command t:3d";
        t.csvName = "Simply1"; t.customCommand = "/simply 2"; t.taskSettings = new Config(); t.csvSettings = new Config();
        global.tasks.add(t); global.taskDelayMs = 1234; global.queueStartSound = "minecraft:ui.toast.in";
        global.save(temp.resolve("config.json")); Config loaded = Config.load(temp.resolve("config.json"));
        assertEquals(1234, loaded.taskDelayMs); assertEquals("/simply 2", loaded.tasks.getFirst().commands.getFirst().command);
        assertEquals("Simply1", loaded.tasks.getFirst().csvName); assertNotNull(loaded.tasks.getFirst().csvSettings);
        Config snapshot = loaded.copy(); loaded.tasks.getFirst().csvName = "changed";
        assertEquals("Simply1", snapshot.tasks.getFirst().csvName);
        assertTrue(loaded.captureCopy().tasks.isEmpty()); assertEquals(1, loaded.tasks.size());
        assertFalse(loaded.resetCsvNameAfterExport);
    }
    @Test void legacyCommandMigratesOnceAndRemovedCommandsStayRemoved() throws Exception {
        Path file = temp.resolve("legacy.json");
        Files.writeString(file, """
            {"tasks":[{"command":"/co l a:chat t:1h","customCommand":"/simply 2","customDelayMs":1234}]}
            """);
        Config c = Config.load(file); TaskDefinition t = c.tasks.getFirst();
        assertTrue(c.acceptSinglePage); assertEquals(1, t.commands.size());
        assertEquals("/simply 2", t.commands.getFirst().command); assertEquals(1234, t.commands.getFirst().delayMs);
        c.normalize(); assertEquals(1, t.commands.size());
        t.commands.clear(); c.save(file); assertTrue(Config.load(file).tasks.getFirst().commands.isEmpty());
        assertFalse(Files.readString(file).contains("customCommand"));
    }
    @Test void singlePageOptionInheritsOrUsesTaskOverrideAndPersists() throws Exception {
        Config c = new Config(); TaskDefinition t = new TaskDefinition();
        assertTrue(t.effective(c).acceptSinglePage);
        c.acceptSinglePage = false; assertFalse(t.effective(c).acceptSinglePage);
        t.universalSettings = false; t.taskSettings = new Config();
        assertTrue(t.effective(c).acceptSinglePage);
        t.taskSettings.acceptSinglePage = false; c.tasks.add(t); c.save(temp.resolve("option.json"));
        Config restored = Config.load(temp.resolve("option.json"));
        assertFalse(restored.acceptSinglePage); assertFalse(restored.tasks.getFirst().taskSettings.acceptSinglePage);
    }
    @Test void acceptedSinglePageExportsAllRecordsAndItsHonestConfirmationFlag() throws Exception {
        var h = new CaptureEngineTest.Harness(); h.body(); h.after(3500);
        assertTrue(h.finalResult.outcome().successful());
        Config c = new Config(); c.csvFileName = "single";
        try (SessionFiles files = new SessionFiles(temp, Language.SPANISH, c)) {
            files.begin(h.finalResult.command(), "test", 1000); files.finish(h.finalResult);
        }
        String csv = Files.readString(temp.resolve("single.csv"));
        assertTrue(csv.contains("minecraft:stone"));
        assertTrue(csv.contains("false"));
        assertTrue(Files.readString(temp.resolve("resumen.txt")).contains("PÁGINA ÚNICA GUARDADA"));
    }
    @Test void commandListOrderAndIndividualDelaysSurviveReload() throws Exception {
        Config c = new Config(); TaskDefinition t = new TaskDefinition();
        t.commands.add(new TaskCommand("/first", 100)); t.commands.add(new TaskCommand("/second", 200));
        java.util.Collections.swap(t.commands, 0, 1); c.tasks.add(t); c.save(temp.resolve("commands.json"));
        var restored = Config.load(temp.resolve("commands.json")).tasks.getFirst().commands;
        assertEquals("/second", restored.getFirst().command); assertEquals(200, restored.getFirst().delayMs);
        assertEquals("/first", restored.getLast().command); assertEquals(100, restored.getLast().delayMs);
    }
    @Test void resetOnlyClearsTheExportedNameAndPreservesNewEdits() {
        assertEquals("julio", CsvFileNames.afterExport("julio", "julio", false));
        assertEquals("", CsvFileNames.afterExport("julio.csv", "julio", true));
        assertEquals("agosto", CsvFileNames.afterExport("agosto", "julio", true));
    }
    @Test void namedFoldersNeverOverwriteExistingExports() throws Exception {
        Path first = CsvFileNames.reserveDirectory(temp, "julio.csv", "legacy");
        Files.writeString(first.resolve("julio.csv"), "preserved");
        assertEquals(temp.resolve("julio"), first);
        assertEquals(temp.resolve("julio (2)"), CsvFileNames.reserveDirectory(temp, "julio", "legacy"));
        assertEquals(temp.resolve("legacy"), CsvFileNames.reserveDirectory(temp, "", "legacy"));
        assertEquals("preserved", Files.readString(first.resolve("julio.csv")));
        assertThrows(IllegalArgumentException.class, () -> CsvFileNames.reserveDirectory(temp, "../escape", "legacy"));
    }
}
