package dev.coretrace.core;

import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TaskProfilesTest {
    @TempDir Path temp;

    private TaskDefinition task(String name) {
        var task = new TaskDefinition(); task.csvName = name; task.command = "/co l a:command t:3d";
        task.universalSettings = false;
        task.taskSettings = new Config(); task.taskSettings.acceptSinglePage = false;
        task.csvSettings = new Config(); task.csvSettings.csvPagesPerFile = 5;
        task.commands.add(new TaskCommand("/simply 2", 1500));
        return task;
    }
    @Test void blankQueueDelayDefaultsTo3000WithoutChangingCommandDefaultsOrSavedValues() throws Exception {
        assertEquals(3000, new Config().taskDelayMs);
        assertEquals(3000, TaskDefinition.taskDelay(" "));
        assertEquals(750, TaskDefinition.delay(""));
        assertEquals(0, TaskDefinition.taskDelay("0"));
        assertEquals(2999, TaskDefinition.taskDelay("2999"));
        assertThrows(IllegalArgumentException.class, () -> TaskDefinition.taskDelay("-1"));
        Files.writeString(temp.resolve("old.json"), "{\"taskDelayMs\":750}");
        assertEquals(750, Config.load(temp.resolve("old.json")).taskDelayMs);
        Files.writeString(temp.resolve("missing.json"), "{}");
        assertEquals(3000, Config.load(temp.resolve("missing.json")).taskDelayMs);
    }
    @Test void duplicateIsInsertedAfterOriginalAndAllEditableStateIsIndependent() {
        var c = new Config(); var original = task("one"); c.tasks.add(original); c.tasks.add(task("two"));
        var duplicate = c.duplicateTask(0);
        assertSame(duplicate, c.tasks.get(1)); assertEquals("two", c.tasks.get(2).csvName);
        assertEquals(original.command, duplicate.command); assertFalse(duplicate.universalSettings);
        duplicate.csvName = "copy"; duplicate.commands.getFirst().command = "/simply 3";
        duplicate.commands.getFirst().delayMs = 99; duplicate.taskSettings.acceptSinglePage = true;
        duplicate.csvSettings.csvColumns.clear(); duplicate.csvSettings.csvPagesPerFile = 1;
        assertEquals("one", original.csvName); assertEquals("/simply 2", original.commands.getFirst().command);
        assertEquals(1500, original.commands.getFirst().delayMs); assertFalse(original.taskSettings.acceptSinglePage);
        assertFalse(original.csvSettings.csvColumns.isEmpty()); assertEquals(5, original.csvSettings.csvPagesPerFile);
    }
    @Test void savedProfilesSurviveClearRestartAndRepeatedLoadsWithoutSharingState() throws Exception {
        var c = new Config(); c.tasks.add(task("one")); c.tasks.add(task("two"));
        c.saveTaskProfile(" First ");
        c.tasks.getFirst().commands.getFirst().command = "/changed";
        c.tasks.clear(); c.save(temp.resolve("config.json"));
        c = Config.load(temp.resolve("config.json"));
        assertTrue(c.tasks.isEmpty()); assertEquals("First", c.taskProfiles.getFirst().name);
        c.loadTaskProfile(c.taskProfiles.getFirst());
        assertEquals(List.of("one", "two"), c.tasks.stream().map(t -> t.csvName).toList());
        assertEquals("/simply 2", c.tasks.getFirst().commands.getFirst().command);
        c.tasks.getFirst().commands.clear(); c.tasks.getFirst().csvSettings.csvPagesPerFile = 20;
        c.loadTaskProfile(c.taskProfiles.getFirst());
        assertEquals(1, c.tasks.getFirst().commands.size()); assertEquals(5, c.tasks.getFirst().csvSettings.csvPagesPerFile);
        c.taskProfiles.clear(); c.save(temp.resolve("config.json"));
        var restored = Config.load(temp.resolve("config.json"));
        assertTrue(restored.taskProfiles.isEmpty()); assertEquals(2, restored.tasks.size());
    }
    @Test void profileNamesCannotSilentlyOverwriteExistingProfiles() {
        var c = new Config(); c.saveTaskProfile("My profile");
        assertThrows(IllegalArgumentException.class, () -> c.saveTaskProfile(" my PROFILE "));
        assertThrows(IllegalArgumentException.class, () -> c.saveTaskProfile(" "));
        assertThrows(IllegalArgumentException.class, () -> c.saveTaskProfile("x".repeat(65)));
        assertEquals(1, c.taskProfiles.size());
    }
    @Test void captureOverridesDoNotCarryEntireSavedProfileLists() {
        var c = new Config(); c.tasks.add(task("one")); c.saveTaskProfile("Saved");
        var capture = c.captureCopy();
        assertTrue(capture.tasks.isEmpty()); assertTrue(capture.taskProfiles.isEmpty());
        assertEquals(1, c.tasks.size()); assertEquals(1, c.taskProfiles.size());
    }
}
