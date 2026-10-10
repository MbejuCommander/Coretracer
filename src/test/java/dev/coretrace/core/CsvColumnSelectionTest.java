package dev.coretrace.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class CsvColumnSelectionTest {
    @TempDir Path temp;

    @Test void clearingAllKeepsTimestampAndDoesNotChangeTheSavedSelection() throws Exception {
        var config = new Config();
        var draft = new CsvColumnSelection(config.csvColumns);
        assertTrue(draft.allSelected());
        draft.toggleAll();
        assertTrue(draft.canSave());
        assertEquals(List.of("server_timestamp"), draft.selected());
        assertEquals(Report.CSV_COLUMNS, config.csvColumns);

        assertTrue(draft.canSave());
        config.csvColumns = new ArrayList<>(draft.selected());
        config.save(temp.resolve("config.json"));
        assertEquals(List.of("server_timestamp"), Config.load(temp.resolve("config.json")).csvColumns);
    }

    @Test void oneButtonSelectsAllFromPartialAndClearsAllFromFullAcrossPages() {
        var draft = new CsvColumnSelection(List.of("raw_text"));
        draft.toggleAll();
        assertTrue(draft.allSelected());
        assertEquals(Report.CSV_COLUMNS, draft.selected());
        draft.toggleAll();
        assertTrue(draft.canSave());
        draft.toggle("server_timestamp");
        assertFalse(draft.canSave());
        draft.toggle("coordinate_hover_text");
        assertEquals(List.of("coordinate_hover_text"), draft.selected());
        draft.toggle("coordinate_hover_text");
        assertFalse(draft.canSave());
    }
}
