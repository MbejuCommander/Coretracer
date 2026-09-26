package dev.coretrace.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class ExportCoverageTest {
    static CaptureEngine.Snapshot capture(String query, Language language, String... lines) {
        var h = new CaptureEngineTest.Harness(query, language);
        h.receive(CaptureEngineTest.HEADER);
        for (String line : lines) assertTrue(h.receive(line), "The capture must retain " + line);
        h.footer(1, 1);
        assertNotNull(h.finalResult);
        assertEquals(CaptureEngine.Outcome.COMPLETE, h.finalResult.outcome());
        return h.finalResult;
    }

    static Stream<Arguments> publicEnglishFormats() {
        // Synthetic fixtures follow the phrases/rendering in the official CE v24.0 source.
        return Stream.of(
            Arguments.of("-block", "jose broke minecraft:stone.", "block", "broke", "block_break", "", "minecraft:stone"),
            Arguments.of("+block", "jose placed oak_log.", "block", "placed", "block_place", "", "oak_log"),
            Arguments.of("session", "jose logged in.", "session", "logged in", "session_login", "", ""),
            Arguments.of("session", "jose logged out.", "session", "logged out", "session_logout", "", ""),
            Arguments.of("click", "jose clicked chest.", "click", "clicked", "interaction", "", "chest"),
            Arguments.of("container", "jose added x64 diamond(↓).", "container", "added", "item_add", "64", "diamond"),
            Arguments.of("container", "jose removed x12 minecraft:iron_ingot.", "container", "removed", "item_remove", "12", "minecraft:iron_ingot"),
            Arguments.of("item", "jose picked up x3 diamond.", "item", "picked up", "item_pickup", "3", "diamond"),
            Arguments.of("item", "jose dropped x8 stone.", "item", "dropped", "item_drop", "8", "stone"),
            Arguments.of("item", "jose deposited x2 ender_pearl.", "item", "deposited", "ender_deposit", "2", "ender_pearl"),
            Arguments.of("item", "jose withdrew x4 emerald.", "item", "withdrew", "ender_withdraw", "4", "emerald"),
            Arguments.of("item", "jose threw x1 snowball.", "item", "threw", "projectile_throw", "1", "snowball"),
            Arguments.of("item", "jose shot x1 arrow.", "item", "shot", "projectile_shoot", "1", "arrow"),
            Arguments.of("inventory", "jose added x1 diamond_sword(↓).", "inventory", "added", "item_add", "1", "diamond_sword"),
            Arguments.of("inventory", "jose removed x8 cobblestone.", "inventory", "removed", "item_remove", "8", "cobblestone"),
            Arguments.of("kill", "jose killed zombie.", "kill", "killed", "entity_kill", "", "zombie")
        );
    }
    @ParameterizedTest @MethodSource("publicEnglishFormats")
    void recognizesEveryObjectAndSessionFormat(String action, String body, String type, String verb,
                                              String actionId, String amount, String object) {
        String original = "0.05/h ago - " + body;
        var snapshot = capture("co l a:" + action + " u:jose t:3d", Language.SPANISH, original);
        var row = Report.rows(snapshot).getFirst(); var e = row.entry();
        assertEquals(type, e.type()); assertEquals("jose", e.actor());
        assertEquals(verb, e.actionText()); assertEquals(actionId, e.action());
        assertEquals(amount, e.amount()); assertEquals(object, e.object());
        assertEquals("parsed", e.parseStatus()); assertEquals(original, row.raw());
        assertEquals("0.05/h ago", e.relativeTime()); assertEquals("-", e.sign());
        assertTrue(Report.csv(snapshot).contains(Report.csvCell(original)));
    }

    @Test void chatAndCommandsUseQueryTypeAndKeepPayloadExactly() {
        String payload = "/say Hello, \"world\"!\nSecond line: mañana 🧱";
        for (String type : List.of("chat", "command", "sign")) {
            var s = capture("co l a:" + type + " u:jose t:3d", Language.SPANISH, "1.0/h ago - jose: " + payload);
            var r = Report.rows(s).getFirst();
            assertEquals(type, r.entry().type()); assertEquals(payload, r.entry().content());
            assertEquals("", r.entry().actionText()); assertEquals("jose", r.actor());
            assertEquals("parsed", r.entry().parseStatus());
            assertTrue(Report.csv(s).contains(Report.csvCell(payload)));
        }
    }
    @Test void emptyChatIsStillAnEvent() {
        var s = capture("co l a:chat t:3d", Language.ENGLISH, "1.0/h ago - jose: ");
        assertEquals(1, Report.rows(s).size()); assertEquals("", Report.rows(s).getFirst().entry().content());
    }
    @Test void doesNotGuessCommandFromSlashOrUnknownQueryContext() {
        for (String query : List.of("co l a:chat,command t:3d", "co l u:jose t:3d", "co l 1")) {
            var s = capture(query, Language.ENGLISH, "1.0/h ago - jose: /tp ana");
            var e = Report.rows(s).getFirst().entry();
            assertEquals("message", e.type()); assertEquals("ambiguous", e.parseStatus());
            assertEquals("/tp ana", e.content());
        }
    }
    @Test void usernameRecordKeepsCurrentActorAndRecordedNameWithoutInventingRenameDirection() {
        var s = capture("co l a:username u:jose t:3d", Language.ENGLISH, "2.00/d ago - jose logged in as OldJose.");
        var e = Report.rows(s).getFirst().entry();
        assertEquals("username", e.type()); assertEquals("jose", e.actor());
        assertEquals("OldJose", e.recordedUsername()); assertEquals("logged in as", e.actionText());
    }
    @Test void signCoordinatesBelongToTheSignAndMissingFieldsStayEmpty() {
        var s = capture("co l a:sign t:3d", Language.ENGLISH,
                "1.0/h ago - jose: Line one\nLine two", "    ^ (x-12/y64/z-40/world_nether)",
                "1.2/h ago - ana: No location sent");
        var rows = Report.rows(s);
        assertEquals("world_nether", rows.getFirst().location().world());
        assertEquals("-12", rows.getFirst().location().x()); assertEquals("64", rows.getFirst().location().y());
        assertEquals("-40", rows.getFirst().location().z());
        assertEquals("", rows.getLast().coordinates()); assertEquals("", rows.getFirst().timestamp());
        assertTrue(Report.csv(s).contains(",\"world_nether\",-12,64,-40,"));
    }
    @Test void coordinateHintClassifiesAnOtherwiseUnknownObjectActionAndKeepsBothTooltips() {
        var h = new CaptureEngineTest.Harness("co l u:jose t:3d", Language.ENGLISH);
        h.receive(CaptureEngineTest.HEADER);
        h.engine.accept(new MessageData("1.0/h ago - jose new-action future_item.", List.of("2026-09-06 01:23:45 UTC", "Item info"), List.of(), h.now, true), h.now);
        h.engine.accept(new MessageData("    ^ (x1/y2/z3/world) (a:item)", List.of("Teleport location"), List.of(), h.now), h.now);
        h.footer(1, 1);
        var r = Report.rows(h.finalResult).getFirst();
        assertEquals("item", r.entry().type()); assertEquals("unrecognized", r.entry().parseStatus());
        assertEquals("2026-09-06 01:23:45 UTC", r.timestamp()); assertTrue(r.struckThrough());
        assertEquals(List.of("Teleport location"), r.coordinateHovers());
        assertTrue(Report.csv(h.finalResult).contains("Item info"));
        assertTrue(Report.csv(h.finalResult).contains("Teleport location"));
        assertTrue(Report.csv(h.finalResult).contains("(a:item)"));
    }
    @Test void unknownActorsAndVerbsAreNotDropped() {
        var s = capture("co l t:3d", Language.ENGLISH, "1.0/h ago - [unknown] unexpected record", "1.0/h ago - jose new-action future_object.");
        assertEquals(2, Report.rows(s).size());
        for (var r : Report.rows(s)) {
            assertEquals("unrecognized", r.entry().parseStatus());
            assertTrue(Report.csv(s).contains(Report.csvCell(r.raw())));
        }
    }
    @Test void inventoryDoesNotInventCraftOrTradeSubtypesHiddenByCoreProtect() {
        var s = capture("co l a:inventory t:3d", Language.ENGLISH, "1.0/h ago + jose added x4 oak_planks.");
        var e = Report.rows(s).getFirst().entry();
        assertEquals("inventory", e.type()); assertEquals("added", e.actionText());
        assertEquals("item_add", e.action()); assertEquals("4", e.amount());
    }
    @Test void csvIsIdenticalInBothMenuLanguagesAndKeepsEnglishServerVerbs() {
        String query = "co l a:container u:jose t:3d";
        String[] lines = {"1.0/h ago - jose removed x12 diamond.", "^ (x1/y64/z-40/world)"};
        String es = Report.csv(capture(query, Language.SPANISH, lines));
        String en = Report.csv(capture(query, Language.ENGLISH, lines));
        assertEquals(en, es); assertTrue(es.startsWith("\uFEFFpage_requested,page_confirmed,query_action,event_type,"));
        assertTrue(es.contains("\"removed\"")); assertFalse(es.contains("retirado"));
    }
    @Test void selectingEnglishDoesNotTranslateOrFoldASpanishServerRecord() {
        String line = "1.0/h ago - jose rompió minecraft:stone.";
        var s = capture("co l a:block t:3d", Language.ENGLISH, line);
        assertEquals("rompió", Report.rows(s).getFirst().entry().actionText());
        assertTrue(Report.csv(s).contains(line)); assertFalse(Report.csv(s).contains("\"broke\""));
    }
    @Test void potentialSpreadsheetFormulaIsProtectedOnlyInCsv() {
        String payload = "=HYPERLINK(\"https://example.invalid\",\"click\")";
        var s = capture("co l a:chat t:3d", Language.ENGLISH, "1.0/h ago - jose: " + payload);
        assertEquals(payload, Report.rows(s).getFirst().entry().content());
        assertEquals("1.0/h ago - jose: " + payload, s.pages().getFirst().messages().get(1).text());
        assertTrue(Report.csv(s).contains("\"'=HYPERLINK("));
    }
}
