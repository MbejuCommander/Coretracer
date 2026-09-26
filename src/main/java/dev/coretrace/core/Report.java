package dev.coretrace.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Report {
    private Report() {}
    public record Row(int page, boolean confirmedPage, EventParser.Entry entry, EventParser.Location location,
                      MessageData message, String rawCoordinates, List<String> coordinateHovers) {
        public Row { coordinateHovers = List.copyOf(coordinateHovers); }
        public String timestamp() { return entry.timestamp(); }
        public String actor() { return entry.actor(); }
        public String action() { return entry.action(); }
        public String material() { return entry.object(); }
        public String coordinates() { return location.coordinates(); }
        public String raw() { return message.text(); }
        public List<String> hovers() { return message.hovers(); }
        public boolean struckThrough() { return message.struckThrough(); }
    }

    /** Stable schema: menu/report language never changes CSV columns or server event text. */
    public static final String CSV_HEADER = "page_requested,page_confirmed,query_action,event_type,server_timestamp,time_relative,actor,action,action_id,action_sign,amount,object,world,x,y,z,content,recorded_username,strikethrough,parse_status,raw_text,raw_coordinates,hover_text,coordinate_hover_text";
    public static final List<String> CSV_COLUMNS = List.of(CSV_HEADER.split(","));

    public static List<Row> rows(CaptureEngine.Snapshot s) {
        List<Row> result = new ArrayList<>();
        var query = EventParser.query(s.command());
        for (var p : s.pages()) appendRows(result, p.number(), p.confirmed(), p.messages(), query);
        appendRows(result, s.pages().isEmpty() ? s.firstPage() : s.pages().getLast().number() + 1, false, s.pending(), query);
        return List.copyOf(result);
    }
    private static void appendRows(List<Row> target, int page, boolean confirmed, List<MessageData> messages, EventParser.Query query) {
        int lastEntry = -1;
        for (MessageData m : messages) {
            if (CoreProtectParser.entry(m)) {
                lastEntry = target.size();
                target.add(new Row(page, confirmed, EventParser.parse(m, query), EventParser.Location.EMPTY, m, "", List.of()));
            } else if (CoreProtectParser.coordinates(m) && lastEntry >= 0) {
                Row old = target.get(lastEntry);
                var location = EventParser.location(m);
                target.set(lastEntry, new Row(page, confirmed, old.entry().withHint(location.hint()), location,
                        old.message(), m.text(), m.hovers()));
                lastEntry = -1;
            } else lastEntry = -1;
        }
    }
    public static String outcome(CaptureEngine.Outcome outcome) { return outcome(outcome, Language.SPANISH); }
    public static String outcome(CaptureEngine.Outcome outcome, Language language) {
        return Translations.text(language, "outcome." + outcome.name().toLowerCase(java.util.Locale.ROOT));
    }
    public static String actionLabel(String action, Language language) { return Translations.text(language, "action." + action); }
    private static String tr(Language language, String key, Object... args) { return Translations.text(language, key, args); }
    private static void line(StringBuilder b, Language language, String key, Object value) {
        b.append(tr(language, "report." + key)).append(": ").append(value).append('\n');
    }
    public static String summary(CaptureEngine.Snapshot s) {
        return summary(s, SessionFiles.csvName(s.reportLanguage()));
    }
    public static String summary(CaptureEngine.Snapshot s, String csvFiles) {
        Language lang = s.reportLanguage();
        var rows = rows(s);
        StringBuilder b = new StringBuilder(tr(lang, "report.title")).append("\n\n");
        line(b, lang, "state", outcome(s.outcome(), lang));
        line(b, lang, "reason", s.detail());
        line(b, lang, "command", "/" + s.command());
        line(b, lang, "server", s.server());
        line(b, lang, "started", Instant.ofEpochMilli(s.startedAt()));
        line(b, lang, "ended", Instant.ofEpochMilli(s.endedAt()));
        line(b, lang, "language", lang.nativeName());
        line(b, lang, "first_page", s.firstPage());
        line(b, lang, "closed_pages", s.pages().size());
        line(b, lang, "total_pages", s.totalPages() > 0 ? s.totalPages() : tr(lang, "report.unannounced"));
        line(b, lang, "pending_lines", s.pending().size());
        line(b, lang, "events", rows.size());
        line(b, lang, "confirmed_events", rows.stream().filter(Row::confirmedPage).count());
        line(b, lang, "unclassified_events", rows.stream().filter(r -> r.action().equals("unclassified")).count());
        line(b, lang, "uncertain_events", rows.stream().filter(r -> !r.entry().parseStatus().equals("parsed")).count());
        line(b, lang, "struck_events", rows.stream().filter(Row::struckThrough).count());
        if (s.firstPage() > 1) b.append(tr(lang, "report.partial_scope", s.firstPage())).append('\n');
        b.append('\n').append(tr(lang, "report.notes")).append('\n');
        b.append('\n').append(tr(lang, "report.by_actor")).append('\n');
        counts(b, rows.stream().map(r -> r.actor().isEmpty() ? tr(lang, "report.unidentified") : r.actor()).toList(), lang);
        b.append('\n').append(tr(lang, "report.by_type")).append('\n');
        counts(b, rows.stream().map(r -> tr(lang, "type." + r.entry().type())).toList(), lang);
        b.append('\n').append(tr(lang, "report.by_action")).append('\n');
        counts(b, rows.stream().map(r -> actionLabel(r.action(), lang)).toList(), lang);
        b.append('\n').append(tr(lang, "report.by_material")).append('\n');
        counts(b, rows.stream().map(Row::material).filter(x -> !x.isBlank()).toList(), lang);
        b.append('\n').append(tr(lang, "report.files", csvFiles, SessionFiles.jsonName(lang))).append('\n');
        return b.toString();
    }
    private static void counts(StringBuilder b, List<String> values, Language language) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (String v : values) totals.merge(v, 1, Integer::sum);
        totals.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey())).limit(100)
                .forEach(e -> b.append(e.getKey()).append(": ").append(e.getValue()).append('\n'));
        if (totals.isEmpty()) b.append(tr(language, "report.no_classifiable")).append('\n');
        if (totals.size() > 100) b.append(tr(language, "report.top100")).append('\n');
    }
    public static String csv(CaptureEngine.Snapshot s) {
        return csv(s, CSV_COLUMNS);
    }
    public static String csv(CaptureEngine.Snapshot s, List<String> columns) {
        List<Integer> selected = CSV_COLUMNS.stream().map(CSV_COLUMNS::indexOf).filter(i -> columns.contains(CSV_COLUMNS.get(i))).toList();
        StringBuilder b = new StringBuilder("\uFEFF").append(String.join(",", selected.stream().map(CSV_COLUMNS::get).toList())).append("\r\n");
        for (Row r : rows(s)) {
            var e = r.entry(); var l = r.location();
            String[] cells = {String.valueOf(r.page()), String.valueOf(r.confirmedPage()), csvCell(e.queryAction()), csvCell(e.type()),
                    csvCell(e.timestamp()), csvCell(e.relativeTime()), csvCell(e.actor()), csvCell(e.actionText()), csvCell(e.action()),
                    quote(e.sign()), String.valueOf(e.amount()), csvCell(e.object()), csvCell(l.world()), String.valueOf(l.x()),
                    String.valueOf(l.y()), String.valueOf(l.z()), csvCell(e.content()), csvCell(e.recordedUsername()),
                    String.valueOf(r.struckThrough()), csvCell(e.parseStatus()), csvCell(r.raw()), csvCell(r.rawCoordinates()),
                    csvCell(String.join("\n", r.hovers())), csvCell(String.join("\n", r.coordinateHovers()))};
            for (int i = 0; i < selected.size(); i++) { if (i > 0) b.append(','); b.append(cells[selected.get(i)]); }
            b.append("\r\n");
        }
        return b.toString();
    }
    public static String csvCell(String s) {
        // Quoting alone does not stop spreadsheet formulas in user-controlled text.
        if (s.stripLeading().matches("(?s)^[=+@-].*")) s = "'" + s;
        return quote(s);
    }
    private static String quote(String s) { return '"' + s.replace("\"", "\"\"") + '"'; }
}
