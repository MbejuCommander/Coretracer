package dev.coretrace.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LocalizationTest {
    @TempDir Path temp;
    @Test void oldConfigurationDefaultsToSpanishWithoutLosingPreferences() throws Exception {
        Path path = temp.resolve("old.json");
        Files.writeString(path, "{\"delayMs\":2500,\"automatic\":false,\"favorites\":[\"co l a:chat t:3d\"]}");
        var c = Config.load(path);
        assertEquals(Language.SPANISH, c.selectedLanguage()); assertEquals(2500, c.delayMs);
        assertFalse(c.automatic); assertEquals(1, c.favorites.size());
    }
    @Test void languageChoicePersistsAndInvalidOrNullCodesFallBack() throws Exception {
        Path path = temp.resolve("config.json"); var c = new Config();
        c.language = "EN-US"; c.save(path);
        assertEquals(Language.ENGLISH, Config.load(path).selectedLanguage());
        assertEquals("en_us", Config.load(path).language);
        c.language = "unknown"; c.normalize(); assertEquals("es_es", c.language);
        c.language = null; c.normalize(); assertEquals("es_es", c.language);
    }
    @Test void catalogsHaveTheSameKeysAndFormattingParameters() {
        var es = Translations.catalog(Language.SPANISH); var en = Translations.catalog(Language.ENGLISH);
        assertEquals(es.keySet(), en.keySet());
        for (String key : es.keySet()) {
            assertFalse(es.get(key).isBlank(), key); assertFalse(en.get(key).isBlank(), key);
            long esArgs = es.get(key).chars().filter(c -> c == '%').count();
            long enArgs = en.get(key).chars().filter(c -> c == '%').count();
            assertEquals(esArgs, enArgs, key);
            Object[] args = java.util.Collections.nCopies((int) esArgs, "TEST").toArray();
            assertDoesNotThrow(() -> String.format(java.util.Locale.ROOT, es.get(key), args), key);
            assertDoesNotThrow(() -> String.format(java.util.Locale.ROOT, en.get(key), args), key);
        }
    }
    @Test void switchingDisplayLanguageDoesNotChangeTheCapturesReportLanguage() {
        var h = new CaptureEngineTest.Harness("co l a:chat t:3d", Language.ENGLISH);
        h.receive(CaptureEngineTest.HEADER); h.receive("1.0/h ago - jose: Hello");
        assertEquals("Reading page 1", h.engine.detail(Language.ENGLISH));
        assertEquals("Leyendo página 1", h.engine.detail(Language.SPANISH));
        h.engine.togglePause(h.now);
        assertTrue(h.engine.detail(Language.ENGLISH).startsWith("Paused"));
        assertTrue(h.engine.detail(Language.SPANISH).startsWith("En pausa"));
        h.engine.endKey(CaptureEngine.Outcome.CANCELLED, "reason.cancelled", h.now);
        assertEquals("en_us", h.finalResult.language()); assertEquals("Cancelled by the user", h.finalResult.detail());
        assertTrue(Report.summary(h.finalResult).contains("LOOKUP SUMMARY"));
        assertTrue(h.engine.detail(Language.SPANISH).contains("usuario"));
    }
    @Test void englishAndSpanishExportsUseSelectedLabelsWithUntouchedServerMessages() throws Exception {
        String original = "1.0/h ago - jose: Keep this English message, please.";
        for (Language language : Language.values()) {
            var s = ExportCoverageTest.capture("co l a:chat t:3d", language, original);
            Path directory = temp.resolve(language.code());
            try (var files = new SessionFiles(directory, language)) {
                files.begin(s.command(), s.server(), s.startedAt());
                for (var page : s.pages()) { for (var m : page.messages()) files.line(page.number(), m); files.page(page); }
                files.finish(s);
            }
            for (String name : List.of("transcript.log", SessionFiles.summaryName(language), SessionFiles.csvName(language), SessionFiles.jsonName(language)))
                assertTrue(Files.size(directory.resolve(name)) > 20, name);
            String log = Files.readString(directory.resolve("transcript.log"));
            assertTrue(log.contains(original));
            assertTrue(log.contains(language == Language.ENGLISH ? "CAPTURE IN PROGRESS" : "CAPTURA EN CURSO"));
            assertTrue(Files.readString(directory.resolve(SessionFiles.csvName(language))).contains(original));
        }
        assertEquals(Files.readString(temp.resolve("es_es/registros.csv")), Files.readString(temp.resolve("en_us/records.csv")));
    }
    @Test void everyOutcomeHasBothTranslations() {
        for (var outcome : CaptureEngine.Outcome.values()) for (Language language : Language.values())
            assertFalse(Report.outcome(outcome, language).contains("coretrace."));
    }
}
