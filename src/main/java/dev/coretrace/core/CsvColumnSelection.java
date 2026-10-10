package dev.coretrace.core;

import java.util.ArrayList;
import java.util.List;

/** Draft CSV column choices; an empty draft can be filled before saving. */
public final class CsvColumnSelection {
    private final List<String> selected;

    public CsvColumnSelection(List<String> initial) {
        selected = new ArrayList<>(initial);
    }

    public boolean allSelected() { return selected.containsAll(Report.CSV_COLUMNS); }
    public boolean canSave() { return !selected.isEmpty(); }
    public boolean contains(String column) { return selected.contains(column); }
    public List<String> selected() { return List.copyOf(selected); }

    public void toggleAll() {
        boolean wasAllSelected = allSelected();
        selected.clear();
        if (!wasAllSelected) selected.addAll(Report.CSV_COLUMNS);
        else selected.add("server_timestamp");
    }

    public void toggle(String column) {
        if (!Report.CSV_COLUMNS.contains(column)) throw new IllegalArgumentException("Unknown CSV column: " + column);
        if (!selected.remove(column)) selected.add(column);
    }
}
