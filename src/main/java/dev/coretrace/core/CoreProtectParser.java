package dev.coretrace.core;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Parses the public chat format of CoreProtect CE 24.0, not its private database. */
public final class CoreProtectParser {
    private CoreProtectParser() {}
    private static final Pattern HEADER = Pattern.compile("(?is)^\\s*-{3,}.*\\bCoreProtect\\b.*-{3,}\\s*$");
    private static final Pattern RATIO = Pattern.compile("(?<![\\d/])(\\d{1,7})\\s*/\\s*(\\d{1,7})(?![\\d/])");
    private static final Pattern LABEL = Pattern.compile("(?iu)^\\s*[◀«]?\\s*(?:page|p[aá]gina|seite|pagina|страница|сторінка|ページ|页|頁|페이지|صفحة|halaman)\\s*:?");
    private static final Pattern TIME = Pattern.compile("^\\s*(?:(?:hace|ha|há)\\s+)?[0-9]+(?:[.,][0-9]+)?\\s*[/]?[^\\s0-9]{1,16}(?:\\s+(?:ago|atrás|atras))?\\s+[+−-]\\s+.+", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
    private static final Pattern STAMP = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\b");
    public static final Pattern COORDS = Pattern.compile("\\(x(-?\\d+)/y(-?\\d+)/z(-?\\d+)(?:/([^\\)]+))?\\)");
    public record Pagination(int current, int total) {}

    public static boolean header(MessageData message) { return HEADER.matcher(message.text()).matches(); }
    public static boolean prefixed(MessageData m) { return m.text().strip().matches("(?is)^(?:\\[CoreProtect\\]|CoreProtect\\s*[-:]).*"); }
    public static boolean entry(MessageData m) {
        return TIME.matcher(m.text()).matches() || (m.hovers().stream().anyMatch(h -> STAMP.matcher(h).find()) && m.text().matches("(?s).*\\s[+−-]\\s.+"));
    }
    public static boolean coordinates(MessageData m) { return m.text().strip().startsWith("^") && COORDS.matcher(m.text()).find(); }

    public static Optional<Pagination> pagination(MessageData m) {
        boolean validLink = m.lookupLinks().stream().anyMatch(link -> LookupCommand.parse(link).filter(c -> !c.query()).isPresent());
        if (!validLink && !LABEL.matcher(m.text()).find()) return Optional.empty();
        if (entry(m) || coordinates(m)) return Optional.empty();
        var match = RATIO.matcher(m.text());
        if (!match.find()) return Optional.empty();
        int current = Integer.parseInt(match.group(1)), total = Integer.parseInt(match.group(2));
        return current > 0 && total >= current ? Optional.of(new Pagination(current, total)) : Optional.empty();
    }

    public static String folded(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
    public static boolean empty(MessageData m) {
        String s = folded(m.text());
        return prefixed(m) && (s.contains("no results found") || s.contains("no se encontraron resultados")
                || s.contains("nenhum resultado encontrado") || s.contains("no hay resultados"));
    }
    public static boolean failure(MessageData m) {
        if (!prefixed(m)) return false;
        String s = folded(m.text());
        return s.contains("permission") || s.contains("permiso") || s.contains("permissao")
                || s.contains("database busy") || s.contains("database is busy") || s.contains("ocupada")
                || s.contains("invalid") || s.contains("invalido") || s.contains("invalida")
                || s.contains("please specify") || s.contains("especificar") || s.contains("not found")
                || s.contains("no results for") || s.contains("no hay resultados para")
                || s.contains("no se encontro") || s.contains("error") || s.contains("does not exist");
    }
}
