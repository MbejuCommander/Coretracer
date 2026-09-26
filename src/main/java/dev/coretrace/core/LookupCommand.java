package dev.coretrace.core;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Only read-only lookup commands can enter the automatic command queue. */
public record LookupCommand(String command, String root, int firstPage, boolean query) {
    private static final Pattern ROOT = Pattern.compile("(?i)(?:coreprotect:)?(?:co|core|coreprotect)");
    private static final Pattern PAGE = Pattern.compile("[1-9][0-9]{0,6}(?::[1-9][0-9]{0,2})?");

    public static boolean isCoreProtect(String input) {
        String s = input.strip().replaceFirst("^/", "");
        return ROOT.matcher(s.split("\\s+", 2)[0]).matches();
    }

    public static Optional<LookupCommand> parse(String input) {
        if (input == null || input.length() > 256 || input.chars().anyMatch(Character::isISOControl)) return Optional.empty();
        String s = input.strip().replaceFirst("^/", "");
        String[] a = s.split("\\s+", 3);
        if (a.length != 3 || !ROOT.matcher(a[0]).matches()) return Optional.empty();
        String sub = a[1].toLowerCase(Locale.ROOT);
        if (!sub.equals("l") && !sub.equals("lookup") && !sub.equals("page")) return Optional.empty();
        if (PAGE.matcher(a[2]).matches()) {
            int page = Integer.parseInt(a[2].split(":")[0]);
            return Optional.of(new LookupCommand(s, a[0], page, false));
        }
        if (sub.equals("page") || !a[2].contains(":")) return Optional.empty();
        // Explicit pages change which part of a lookup is captured. Require page 1
        // for new queries so a partial query is never presented as complete.
        for (String arg : a[2].split("\\s+")) {
            if (arg.matches("(?i)(?:p|page):.*") || arg.equalsIgnoreCase("#count")) return Optional.empty();
        }
        return Optional.of(new LookupCommand(s, a[0], 1, true));
    }

    public String pageCommand(int number) {
        if (number < 1 || number > 9_999_999) throw new IllegalArgumentException("Página inválida");
        return root + " l " + number;
    }

    public static Optional<LookupCommand> fromInput(String value) {
        String s = value.strip();
        if (!s.startsWith("/") && !isCoreProtect(s)) s = "co l " + s;
        return parse(s);
    }
}
