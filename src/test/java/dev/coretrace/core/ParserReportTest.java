package dev.coretrace.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ParserReportTest {
    @TempDir Path temp;
    @Test void acceptsUserExampleAndNamespacedAliases() {
        assertTrue(LookupCommand.parse("/co l a:-block u:jose t:3d").orElseThrow().query());
        assertEquals("coreprotect:co l 12",LookupCommand.parse("/coreprotect:co lookup u:Ana t:3d").orElseThrow().pageCommand(12));
        assertEquals(3,LookupCommand.parse("/co page 3").orElseThrow().firstPage());
    }
    @Test void rejectsCommandsThatAreNotReadOnlyLookup() {
        for(String s:List.of("co rollback u:jose t:3d","co restore u:jose t:3d","co purge t:3d","co teleport 2 2 2","op jose","co l 2\nco rollback t:3d","co l 0","co l p:3 u:jose t:3d","co l u:jose t:3d #count"))
            assertTrue(LookupCommand.parse(s).isEmpty(),s);
    }
    @Test void paginationSupportsEnglishSpanishPortugueseAndNonLatinLabelsWithLinks() {
        for(String s:List.of("Page 1/15 ▶ (1 | 2 | 15)","Página 3/15","Página: 10/15","Seite 2/15"))
            assertEquals(15,CoreProtectParser.pagination(MessageData.plain(s,1)).orElseThrow().total());
        var chinese=new MessageData("第 2/15 页",List.of(),List.of("/co l 3"),1);
        assertEquals(2,CoreProtectParser.pagination(chinese).orElseThrow().current());
        assertTrue(CoreProtectParser.pagination(MessageData.plain("Page 4/2",1)).isEmpty());
    }
    @Test void coordinatesAreNotPaginationAndTeleportIsNotALookup() {
        assertTrue(CoreProtectParser.pagination(MessageData.plain("^ (x1/y12/z-34/world)",1)).isEmpty());
        assertTrue(LookupCommand.parse("co teleport wid:1 1 64 1").isEmpty());
    }
    @Test void unicodeLanguageEntryCanBeRecognizedByTimestampHover() {
        var m=new MessageData("3分前 - jose 石を破壊した",List.of("2026-09-06 01:23:45 UTC"),List.of(),1);
        assertTrue(CoreProtectParser.entry(m));
    }
    @Test void preservesCoordinatesAndTimestampWhileCountingEvents() {
        var h=new CaptureEngineTest.Harness();h.receive(CaptureEngineTest.HEADER);
        h.engine.accept(new MessageData("0,05/h - jose rompió minecraft:stone.",List.of("2026-09-06 01:23:45 UTC"),List.of(),h.now),h.now);
        h.receive("^ (x12/y64/z-40/world)");h.footer(1,1);
        var r=Report.rows(h.finalResult).getFirst();
        assertEquals("jose",r.actor());assertEquals("block_break",r.action());assertEquals("minecraft:stone",r.material());
        assertEquals("(x12/y64/z-40/world)",r.coordinates());assertEquals("2026-09-06 01:23:45 UTC",r.timestamp());
    }
    @Test void unknownActionsRemainInTranscriptAndCsv() {
        var h=new CaptureEngineTest.Harness();h.receive(CaptureEngineTest.HEADER);h.receive("1.0/h ago - jose unknown-action unknown-object");h.footer(1,1);
        assertEquals("unclassified",Report.rows(h.finalResult).getFirst().action());
        assertTrue(Report.csv(h.finalResult).contains("unknown-action"));
    }
    @Test void csvEscapesCommasQuotesNewlinesAndFormulaPrefixes() {
        assertEquals("\"a,\"\"b\"\"\nc\"",Report.csvCell("a,\"b\"\nc"));
        assertEquals("\"'=1+2\"",Report.csvCell("=1+2"));
        assertEquals("\"'  @SUM(A1)\"",Report.csvCell("  @SUM(A1)"));
    }
    @Test void crashJournalExistsBeforeFinalExport() throws Exception {
        Path dir=temp.resolve("session");
        try(var f=new SessionFiles(dir)) {
            f.begin("co l u:jose t:3d","example.test",1);
            f.line(1,MessageData.plain("1.0/h ago - jose broke minecraft:stone.",2));
            String log=Files.readString(dir.resolve("transcript.log"));
            assertTrue(log.contains("CAPTURA EN CURSO"));assertTrue(log.contains("jose broke"));
            assertFalse(Files.exists(dir.resolve("resumen.txt")));
        }
    }
    @Test void exportsAllFourFilesAndMarksPartialStatus() throws Exception {
        var h=new CaptureEngineTest.Harness();h.body();h.engine.end(CaptureEngine.Outcome.CANCELLED,"user cancelled",h.now);
        Path dir=temp.resolve("session");
        try(var f=new SessionFiles(dir)) {
            f.begin(h.finalResult.command(),h.finalResult.server(),h.finalResult.startedAt());
            for(var m:h.logged)f.line(1,m);
            f.finish(h.finalResult);
        }
        for(String name:List.of("transcript.log","resumen.txt","registros.csv","sesion.json"))assertTrue(Files.size(dir.resolve(name))>20);
        assertTrue(Files.readString(dir.resolve("transcript.log")).contains("FINAL: PARCIAL"));
        assertTrue(Files.readString(dir.resolve("resumen.txt")).contains("CANCELADA"));
        assertTrue(Files.readString(dir.resolve("sesion.json")).contains("CANCELLED"));
    }
    @Test void normalizesMalformedConfigurationAndKeepsValidHistoryOnly() {
        var c=new Config();c.delayMs=-1;c.timeoutMs=1;c.settleMs=99999;c.maxPages=99999;
        c.recentQueries=new java.util.ArrayList<>(List.of("co rollback u:jose t:3d","co l u:jose t:3d"));c.normalize();
        assertEquals(750,c.delayMs);assertEquals(5000,c.timeoutMs);assertEquals(4000,c.settleMs);assertEquals(5000,c.maxPages);
        assertEquals(1,c.recentQueries.size());
    }
    @Test void malformedConfigIsBackedUp() throws Exception {
        Path config=temp.resolve("config.json");Files.writeString(config,"{invalid");
        assertThrows(java.io.IOException.class,()->Config.load(config));
        try(var files=Files.list(temp)){assertEquals(2,files.count());}
    }
    @Test void whitespaceAndChatColorsDoNotBreakRecognition() {
        var m=MessageData.plain("§bPage §f1/15",1);
        assertEquals("Page 1/15",m.text());assertEquals(15,CoreProtectParser.pagination(m).orElseThrow().total());
    }
    @Test void capturesStartingAtPageThreeClearlyDeclareScope() {
        final CaptureEngine.Snapshot[] result={null};
        var sink=new CaptureEngine.Sink() {
            public void send(String s){} public void line(int p,MessageData m){} public void page(CaptureEngine.Page p){}
            public void finish(CaptureEngine.Snapshot s){result[0]=s;}
        };
        var e=new CaptureEngine(LookupCommand.parse("co l 3").orElseThrow(),new Config().settings(),sink,"server",1);
        e.accept(MessageData.plain(CaptureEngineTest.HEADER,2),2);e.accept(MessageData.plain(CaptureEngineTest.ENTRY,3),3);
        e.accept(MessageData.plain("Page 3/3",4),4);
        assertTrue(Report.summary(result[0]).contains("ALCANCE PARCIAL"));
    }
}
