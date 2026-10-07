package dev.coretrace.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class Config {
    public String language = "es_es";
    public boolean automatic = true;
    public boolean hideCapturedChat = false;
    public int delayMs = 1500;
    public int timeoutMs = 30000;
    public int settleMs = 3500;
    public int maxPages = 500;
    public boolean captureHovers = true;
    public boolean acceptSinglePage = true;
    public List<String> csvColumns = new ArrayList<>(Report.CSV_COLUMNS);
    public int csvPagesPerFile = 0;
    public String csvFileName = "";
    public boolean resetCsvNameAfterExport = false;
    public List<TaskDefinition> tasks = new ArrayList<>();
    public int taskDelayMs = 3000;
    public List<TaskProfile> taskProfiles = new ArrayList<>();
    public String queueStartSound = "";
    public String queueFinishSound = "";
    public Config copy() { return new Gson().fromJson(new Gson().toJson(this), Config.class); }
    public Config captureCopy() {
        Config c = copy(); c.tasks.clear(); c.taskProfiles.clear(); return c;
    }
    public TaskDefinition duplicateTask(int index) {
        TaskDefinition duplicate = tasks.get(index).copy();
        tasks.add(index + 1, duplicate);
        return duplicate;
    }
    public void saveTaskProfile(String name) {
        String clean = TaskProfile.validName(name);
        if (taskProfiles.stream().anyMatch(p -> p.name.equalsIgnoreCase(clean)))
            throw new IllegalArgumentException("Profile name already exists");
        taskProfiles.add(new TaskProfile(clean, tasks));
    }
    public void loadTaskProfile(TaskProfile profile) {
        tasks = TaskProfile.copyTasks(profile.tasks);
    }
    public String startSound = "minecraft:ui.toast.in";
    public String finishSound = "minecraft:ui.toast.challenge_complete";
    public String lastExportFile = "";
    public List<String> recentQueries = new ArrayList<>();
    public List<String> favorites = new ArrayList<>();
    public void normalize() {
        language = Language.fromCode(language).code();
        if (tasks == null) tasks = new ArrayList<>();
        tasks.removeIf(java.util.Objects::isNull);
        tasks.forEach(TaskDefinition::migrateCommands);
        if (taskProfiles == null) taskProfiles = new ArrayList<>();
        taskProfiles.removeIf(java.util.Objects::isNull);
        taskProfiles.forEach(TaskProfile::normalize);
        taskDelayMs = Math.clamp(taskDelayMs, 0, 3600000);
        queueStartSound = normalizeSound(queueStartSound);
        queueFinishSound = normalizeSound(queueFinishSound);
        delayMs = Math.clamp(delayMs, 750, 30000);
        timeoutMs = Math.clamp(timeoutMs, 5000, 180000);
        settleMs = Math.clamp(settleMs, 1500, timeoutMs - 1000);
        maxPages = Math.clamp(maxPages, 1, 5000);
        if (csvColumns == null || csvColumns.isEmpty()) csvColumns = new ArrayList<>(Report.CSV_COLUMNS);
        else csvColumns = new ArrayList<>(Report.CSV_COLUMNS.stream().filter(csvColumns::contains).toList());
        if (csvColumns.isEmpty()) csvColumns = new ArrayList<>(Report.CSV_COLUMNS);
        csvPagesPerFile = Math.clamp(csvPagesPerFile, 0, 5000);
        try { csvFileName = CsvFileNames.normalize(csvFileName); }
        catch (IllegalArgumentException e) { csvFileName = ""; }
        startSound = normalizeSound(startSound);
        finishSound = normalizeSound(finishSound);
        if (lastExportFile == null) lastExportFile = "";
        recentQueries = valid(recentQueries, 20); favorites = valid(favorites, 20);
    }
    private static String normalizeSound(String sound) {
        if (sound == null || sound.isBlank()) return "";
        return switch (sound) {
            case "minecraft:ui_toast_in" -> "minecraft:ui.toast.in";
            case "minecraft:ui_toast_challenge_complete" -> "minecraft:ui.toast.challenge_complete";
            case "minecraft:entity_experience_orb_pickup" -> "minecraft:entity.experience_orb.pickup";
            case "minecraft:block_note_block_chime" -> "minecraft:block.note_block.chime";
            case "minecraft:entity_player_levelup" -> "minecraft:entity.player.levelup";
            case "minecraft:block_amethyst_block_chime" -> "minecraft:block.amethyst_block.chime";
            default -> sound;
        };
    }
    private static List<String> valid(List<String> items, int max) {
        if (items == null) return new ArrayList<>();
        return new ArrayList<>(items.stream().filter(x -> x != null && LookupCommand.parse(x).isPresent()).distinct().limit(max).toList());
    }
    public Language selectedLanguage() { return Language.fromCode(language); }
    public CaptureEngine.Settings settings() { normalize(); return new CaptureEngine.Settings(delayMs, timeoutMs, settleMs, maxPages, selectedLanguage(), acceptSinglePage); }
    public void remember(String command) {
        recentQueries.remove(command); recentQueries.addFirst(command); normalize();
    }
    public static Config load(Path path) throws IOException {
        if (!Files.exists(path)) return new Config();
        try {
            Config c = new Gson().fromJson(Files.readString(path, StandardCharsets.UTF_8), Config.class);
            if (c == null) throw new IllegalArgumentException("Empty configuration");
            c.normalize(); return c;
        } catch (RuntimeException e) {
            Path backup = path.resolveSibling(path.getFileName() + ".invalid-" + System.currentTimeMillis());
            Files.copy(path, backup);
            throw new IOException("Invalid configuration; backup: " + backup.getFileName(), e);
        }
    }
    public void save(Path path) throws IOException {
        normalize(); Files.createDirectories(path.getParent());
        SessionFiles.writeAtomic(path, new GsonBuilder().setPrettyPrinting().create().toJson(this));
    }
}
