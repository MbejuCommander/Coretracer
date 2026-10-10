package dev.coretrace.core;

public final class TaskDefinition {
    public String command = "";
    public String csvName = "";
    public boolean universalSettings = true;
    public Config taskSettings;
    public Config csvSettings;
    /** Legacy fields are cleared after migration and omitted from saved JSON. */
    public String customCommand;
    public java.util.List<TaskCommand> commands = new java.util.ArrayList<>();
    public Integer customDelayMs;

    public TaskDefinition copy() {
        TaskDefinition copy = new TaskDefinition();
        copy.command = command;
        copy.csvName = csvName;
        copy.universalSettings = universalSettings;
        copy.taskSettings = taskSettings == null ? null : taskSettings.captureCopy();
        copy.csvSettings = csvSettings == null ? null : csvSettings.captureCopy();
        if (commands != null) for (TaskCommand item : commands)
            copy.commands.add(new TaskCommand(item.command, item.delayMs));
        copy.customCommand = customCommand;
        copy.customDelayMs = customDelayMs;
        copy.migrateCommands();
        return copy;
    }

    public static int taskDelay(String value) {
        return value.isBlank() ? 3000 : delay(value);
    }

    public Config effective(Config universal) {
        Config result = (universalSettings || taskSettings == null ? universal : taskSettings).captureCopy();
        Config csv = csvSettings == null ? universal : csvSettings;
        result.csvColumns = new java.util.ArrayList<>(csv.csvColumns);
        result.csvPagesPerFile = csv.csvPagesPerFile;
        result.csvExcelAutoColumns = csv.csvExcelAutoColumns;
        result.csvSmart = csv.csvSmart;
        result.resetCsvNameAfterExport = csv.resetCsvNameAfterExport;
        result.csvFileName = CsvFileNames.normalize(csvName);
        if (universalSettings || taskSettings == null) {
            result.startSound = ""; result.finishSound = "";
        }
        result.normalize();
        return result;
    }
    public static int delay(String value) {
        int ms = value.isBlank() ? 750 : Integer.parseInt(value.strip());
        if (ms < 0 || ms > 3600000) throw new IllegalArgumentException("Delay: 0–3600000 ms");
        return ms;
    }
    public void validate() {
        if (command == null || LookupCommand.parse(command).filter(LookupCommand::query).isEmpty())
            throw new IllegalArgumentException("Invalid CoreProtect query");
        CsvFileNames.normalize(csvName);
        migrateCommands();
        for (TaskCommand item : commands) {
            if (item == null) throw new IllegalArgumentException("Invalid command entry");
            item.validate();
        }
    }
    public void migrateCommands() {
        if (commands == null) commands = new java.util.ArrayList<>();
        if (customCommand != null && !customCommand.isBlank() && commands.isEmpty())
            commands.add(new TaskCommand(customCommand, customDelayMs == null ? 750 : customDelayMs));
        customCommand = null; customDelayMs = null;
    }
}
