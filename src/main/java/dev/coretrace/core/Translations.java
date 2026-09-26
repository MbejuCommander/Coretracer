package dev.coretrace.core;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Shared catalogs for menus, chat notifications and background file exports. */
public final class Translations {
    private static final Map<Language, Map<String, String>> CATALOGS = load();
    private Translations() {}
    private static Map<Language, Map<String, String>> load() {
        Map<Language, Map<String, String>> result = new EnumMap<>(Language.class);
        for (Language language : Language.values()) {
            String resource = "/assets/coretrace/lang/" + language.code() + ".json";
            var stream = Translations.class.getResourceAsStream(resource);
            if (stream == null) throw new IllegalStateException("Missing translation catalog: " + resource);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                Map<String, String> entries = new HashMap<>();
                JsonParser.parseReader(reader).getAsJsonObject().entrySet()
                        .forEach(e -> entries.put(e.getKey(), e.getValue().getAsString()));
                result.put(language, Map.copyOf(entries));
            } catch (IOException e) { throw new IllegalStateException("Cannot read " + resource, e); }
        }
        return Map.copyOf(result);
    }
    public static String text(Language language, String key, Object... args) {
        if (!key.startsWith("coretrace.")) key = "coretrace." + key;
        Map<String, String> catalog = CATALOGS.get(language == null ? Language.SPANISH : language);
        String pattern = catalog.getOrDefault(key, CATALOGS.get(Language.ENGLISH).getOrDefault(key, key));
        return args.length == 0 ? pattern : String.format(Locale.ROOT, pattern, args);
    }
    public static Map<String, String> catalog(Language language) { return CATALOGS.get(language); }
}
