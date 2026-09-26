package dev.coretrace.core;

import java.util.Locale;

/** An explicit mod preference, independent of Minecraft and the server language. */
public enum Language {
    SPANISH("es_es", "Español"), ENGLISH("en_us", "English");
    private final String code;
    private final String nativeName;
    Language(String code, String nativeName) { this.code = code; this.nativeName = nativeName; }
    public String code() { return code; }
    public String nativeName() { return nativeName; }
    public Language next() { return this == SPANISH ? ENGLISH : SPANISH; }
    public static Language fromCode(String code) {
        if (code == null) return SPANISH;
        String value = code.toLowerCase(Locale.ROOT).replace('-', '_');
        return value.equals("en_us") || value.equals("en") ? ENGLISH : SPANISH;
    }
}
