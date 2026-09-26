import com.google.gson.GsonBuilder;
import dev.coretrace.core.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Generates synthetic examples through the production capture engine and exporter. No network or game needed. */
public class GenerateExamples {
    static final String HEADER = "----- CoreProtect | Lookup Results -----";
    static final long START = Instant.parse("2026-09-06T12:00:00Z").toEpochMilli();
    static CaptureEngine.Snapshot simulate(String query, Language language, List<List<String>> pages) {
        List<String> requests = new ArrayList<>();
        CaptureEngine.Snapshot[] result = {null};
        var sink = new CaptureEngine.Sink() {
            public void send(String command) { requests.add(command); }
            public void line(int page, MessageData message) {}
            public void page(CaptureEngine.Page page) {}
            public void finish(CaptureEngine.Snapshot snapshot) { result[0] = snapshot; }
        };
        var engine = new CaptureEngine(LookupCommand.parse(query).orElseThrow(),
                new CaptureEngine.Settings(1500, 30000, 3500, 500, language), sink, "SIMULATED.example.invalid", START);
        long now = START;
        for (int i = 0; i < pages.size(); i++) {
            engine.accept(MessageData.plain(HEADER, ++now), now);
            for (String line : pages.get(i)) {
                now++;
                var hovers = line.contains("/h ago") ? List.of("2026-09-06 11:57:00 UTC") : List.<String>of();
                if (!engine.accept(new MessageData(line, hovers, List.of(), now), now))
                    throw new IllegalStateException("Fixture was not captured: " + line);
            }
            engine.accept(MessageData.plain("Page " + (i + 1) + "/" + pages.size(), ++now), now);
            if (i < pages.size() - 1) { now += 1500; engine.tick(now); }
        }
        if (result[0] == null || result[0].outcome() != CaptureEngine.Outcome.COMPLETE || requests.size() != pages.size() - 1)
            throw new IllegalStateException("Synthetic pagination failed");
        return result[0];
    }
    static void save(Path directory, CaptureEngine.Snapshot snapshot) throws Exception {
        try (var files = new SessionFiles(directory, snapshot.reportLanguage())) {
            files.begin(snapshot.command(), snapshot.server(), snapshot.startedAt());
            for (var page : snapshot.pages()) {
                for (var m : page.messages()) files.line(page.number(), m);
                files.page(page);
            }
            files.finish(snapshot);
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Pass a new, nonexistent output directory");
        Path root = Path.of(args[0]); Files.createDirectory(root);
        List<List<String>> blockPages = new ArrayList<>();
        for (int page = 1; page <= 15; page++) {
            List<String> lines = new ArrayList<>();
            for (int event = 0; event < 4; event++) {
                lines.add("0.05/h ago - jose broke minecraft:" + (event % 2 == 0 ? "stone" : "oak_log") + ".");
                lines.add("    ^ (x" + (page * 4 + event) + "/y64/z-40/world) (a:block)");
            }
            blockPages.add(lines);
        }
        save(root.resolve("es"), simulate("co l a:-block u:jose t:3d", Language.SPANISH, blockPages));
        save(root.resolve("en"), simulate("co l a:-block u:jose t:3d", Language.ENGLISH, blockPages));
        String[][] cases = {
            {"block", "0.05/h ago - jose broke minecraft:stone.", "^ (x12/y64/z-40/world)", "0.05/h ago + jose placed oak_log."},
            {"session", "0.05/h ago + jose logged in.", "^ (x12/y64/z-40/world)", "0.05/h ago - jose logged out."},
            {"chat", "0.05/h ago - jose: Hello, \"world\"!", "0.05/h ago - jose: =1+2"},
            {"command", "0.05/h ago - jose: /say Server maintenance in 10 minutes"},
            {"click", "0.05/h ago - jose clicked chest.", "^ (x13/y64/z-40/world)"},
            {"container", "0.05/h ago + jose added x64 diamond(↓).", "^ (x13/y64/z-40/world)", "0.05/h ago - jose removed x12 iron_ingot."},
            {"item", "0.05/h ago + jose picked up x3 diamond.", "0.05/h ago - jose dropped x8 stone.",
                "0.05/h ago - jose deposited x2 ender_pearl.", "0.05/h ago + jose withdrew x4 emerald.",
                "0.05/h ago - jose threw x1 snowball.", "0.05/h ago - jose shot x1 arrow."},
            {"inventory", "0.05/h ago + jose added x4 oak_planks.", "0.05/h ago - jose removed x1 oak_log."},
            {"kill", "0.05/h ago - jose killed zombie.", "^ (x-12/y64/z-40/world_nether)"},
            {"sign", "0.05/h ago - jose: Welcome!\nShop, \"diamonds\"\n2 per stack\nThank you", "^ (x14/y64/z-40/world)"},
            {"username", "0.05/h ago - jose logged in as OldJose."}
        };
        var snapshots = new ArrayList<CaptureEngine.Snapshot>();
        StringBuilder combined = new StringBuilder("\uFEFF" + Report.CSV_HEADER + "\r\n");
        for (String[] item : cases) {
            var snapshot = simulate("co l a:" + item[0] + " u:jose t:3d", Language.SPANISH,
                    List.of(List.of(item).subList(1, item.length)));
            snapshots.add(snapshot);
            String csv = Report.csv(snapshot);
            combined.append(csv.substring(csv.indexOf("\r\n") + 2));
        }
        Files.writeString(root.resolve("all-event-types.csv"), combined);
        Files.writeString(root.resolve("all-event-types.json"), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(snapshots));
        int count = snapshots.stream().mapToInt(s -> Report.rows(s).size()).sum();
        System.out.println("Synthetic examples: 15 pages / 60 block records per language; " + count + " mixed records from " + cases.length + " queries.");
    }
}
