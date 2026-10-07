package dev.coretrace.core;

import java.util.Locale;

/** Portable file names; the user supplies a name, never a filesystem path. */
public final class CsvFileNames {
    private CsvFileNames() {}
    public static String normalize(String input) {
        if (input == null || input.isBlank()) return "";
        String name = input.strip();
        if (name.toLowerCase(Locale.ROOT).endsWith(".csv")) name = name.substring(0, name.length() - 4);
        if (name.isBlank() || name.length() > 100 || name.endsWith(".") || name.endsWith(" ")
                || name.chars().anyMatch(c -> c < 32 || "<>:\"/\\|?*".indexOf(c) >= 0))
            throw new IllegalArgumentException("Invalid CSV name");
        String device = name.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        if (device.matches("CON|PRN|AUX|NUL|CLOCK\\$|CONIN\\$|CONOUT\\$|COM[1-9¹²³]|LPT[1-9¹²³]"))
            throw new IllegalArgumentException("Reserved Windows filename");
        return name;
    }
    public static String afterExport(String current, String exported, boolean reset) {
        if (!reset) return current;
        try { return normalize(current).equals(normalize(exported)) ? "" : current; }
        catch (IllegalArgumentException e) { return current; }
    }
    public static java.nio.file.Path reserveDirectory(java.nio.file.Path root, String csvName, String fallback) throws java.io.IOException {
        java.nio.file.Files.createDirectories(root);
        String stem = normalize(csvName);
        if (stem.isEmpty()) stem = fallback;
        for (int i = 1; ; i++) {
            java.nio.file.Path candidate = root.resolve(stem + (i == 1 ? "" : " (" + i + ")"));
            try { return java.nio.file.Files.createDirectory(candidate); }
            catch (java.nio.file.FileAlreadyExistsException ignored) { }
        }
    }
    public static String fileName(String name, Language language, int part) {
        String stem = normalize(name);
        if (stem.isEmpty()) stem = language == Language.ENGLISH ? "records" : "registros";
        return stem + (part > 0 ? "_" + part : "") + ".csv";
    }
}
