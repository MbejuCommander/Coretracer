package dev.coretrace.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Decodes the public CE lookup messages. Never translates server text or invents hidden fields. */
public final class EventParser {
    private EventParser() {}
    private static final Pattern ENVELOPE = Pattern.compile("^\\s*(.+?)\\s+([+−-])\\s+(.+)$", Pattern.DOTALL);
    private static final String ACTOR = "[A-Za-z0-9_.$#~\\-]{1,64}";
    private static final Pattern MESSAGE = Pattern.compile("^(" + ACTOR + "): ?(.*)$", Pattern.DOTALL);
    private static final Pattern BODY = Pattern.compile("^(" + ACTOR + ")\\s+(.+)$", Pattern.DOTALL);
    private static final Pattern TIME = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(?: [A-Za-z0-9:+_/-]+)?");
    private static final Pattern ACTION_PARAM = Pattern.compile("(?i)(?:^|\\s)((?:a|action):([^\\s]+))");
    private static final Pattern ACTION_HINT = Pattern.compile("(?i)\\(a:(block|container|item|click|kill)\\)");
    private static final Pattern QUANTITY = Pattern.compile("^x([0-9]+)\\s+(.+)$", Pattern.DOTALL);

    public record Query(String raw, Set<String> types) {
        public Query { types = Set.copyOf(types); }
        String singleType() { return types.size() == 1 ? types.iterator().next() : "unknown"; }
    }
    public record Entry(String queryAction, String type, String timestamp, String relativeTime,
                        String actor, String action, String actionText, String sign, String amount,
                        String object, String content, String recordedUsername, String parseStatus) {
        Entry withHint(String hint) {
            if (hint.isEmpty() || type.equals("inventory") || !content.isEmpty()) return this;
            return new Entry(queryAction, hint, timestamp, relativeTime, actor, action, actionText,
                    sign, amount, object, content, recordedUsername, parseStatus);
        }
    }
    public record Location(String coordinates, String world, String x, String y, String z, String hint) {
        public static final Location EMPTY = new Location("", "", "", "", "", "");
    }
    private record Verb(String id, String type, boolean quantity, Pattern prefix) {}
    private static Verb verb(String id, String type, boolean quantity, String alternatives) {
        return new Verb(id, type, quantity, Pattern.compile("^(" + alternatives + ")(?=\\s|[.。]?$)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
    }
    private static final List<Verb> VERBS = List.of(
            verb("username_observed", "username", false, "logged in as"),
            verb("session_login", "session", false, "logged in"),
            verb("session_logout", "session", false, "logged out"),
            verb("block_break", "block", false, "broke|rompió|rompio|ha roto|quebrou|destruiu"),
            verb("block_place", "block", false, "placed|colocó|coloco|ha colocado|colocou"),
            verb("item_pickup", "item", true, "picked up"),
            verb("item_drop", "item", true, "dropped"),
            verb("ender_deposit", "item", true, "deposited"),
            verb("ender_withdraw", "item", true, "withdrew"),
            verb("projectile_throw", "item", true, "threw"),
            verb("projectile_shoot", "item", true, "shot"),
            verb("item_remove", "container", true, "removed|retiró de|retiro de|retiró|retiro|removeu"),
            verb("item_add", "container", true, "added|añadió|anadio|agregó|agrego|adicionou"),
            verb("interaction", "click", false, "clicked|interactuó|interactuo|clicou"),
            verb("entity_kill", "kill", false, "killed|mató|mato|matou")
    );

    public static Query query(String command) {
        Set<String> types = new LinkedHashSet<>();
        List<String> raw = new ArrayList<>();
        var matcher = ACTION_PARAM.matcher(command);
        while (matcher.find()) {
            raw.add(matcher.group(1));
            for (String token : matcher.group(2).split(",")) {
                String type = token.toLowerCase(Locale.ROOT).replaceFirst("^[+−-]", "");
                type = switch (type) {
                    case "blocks" -> "block";
                    case "sessions" -> "session";
                    case "commands" -> "command";
                    case "containers" -> "container";
                    case "items" -> "item";
                    case "usernames" -> "username";
                    case "interaction", "interact" -> "click";
                    default -> type;
                };
                if (!type.isBlank()) types.add(type);
            }
        }
        return new Query(String.join(" ", raw), types);
    }

    public static Entry parse(MessageData message, Query query) {
        String timestamp = message.hovers().stream().map(TIME::matcher).filter(java.util.regex.Matcher::find)
                .map(java.util.regex.Matcher::group).findFirst().orElse("");
        String time = "", sign = "", actor = "";
        var envelope = ENVELOPE.matcher(message.text());
        if (envelope.matches()) {
            time = envelope.group(1).strip(); sign = envelope.group(2);
            String body = envelope.group(3);
            var content = MESSAGE.matcher(body);
            if (content.matches()) {
                // CE renders chat and command with the same prefix. Only the query can distinguish them.
                String type = query.singleType();
                boolean known = Set.of("chat", "command", "sign").contains(type);
                if (!known) type = "message";
                return new Entry(query.raw(), type, timestamp, time, content.group(1),
                        known ? (type.equals("sign") ? "sign_text" : type) : "message", "", sign,
                        "", "", content.group(2), "", known ? "parsed" : "ambiguous");
            }
            var named = BODY.matcher(body);
            if (named.matches()) {
                actor = named.group(1);
                String rest = named.group(2);
                for (Verb verb : VERBS) {
                    var match = verb.prefix().matcher(rest);
                    if (!match.find()) continue;
                    String actionText = match.group(1);
                    String tail = rest.substring(match.end()).strip().replaceFirst("[.。]$", "").strip();
                    if (verb.type().equals("session")) {
                        return new Entry(query.raw(), "session", timestamp, time, actor, verb.id(), actionText,
                                sign, "", "", "", "", tail.isEmpty() ? "parsed" : "partial");
                    }
                    if (verb.type().equals("username")) {
                        return new Entry(query.raw(), "username", timestamp, time, actor, verb.id(), actionText,
                                sign, "", "", "", tail, tail.isEmpty() ? "partial" : "parsed");
                    }
                    String amount = "";
                    var quantity = QUANTITY.matcher(tail);
                    if (quantity.matches()) { amount = quantity.group(1); tail = quantity.group(2); }
                    // This is an optional visible give-item button, not part of the object identifier.
                    String object = tail.replaceFirst("\\s*\\(↓\\)$", "").strip();
                    String type = query.singleType().equals("inventory") ? "inventory" : verb.type();
                    return new Entry(query.raw(), type, timestamp, time, actor, verb.id(), actionText, sign,
                            amount, object, "", "", object.isEmpty() || (verb.quantity() && amount.isEmpty()) ? "partial" : "parsed");
                }
            }
        }
        String type = query.singleType();
        if (!Set.of("block", "session", "chat", "command", "click", "container", "item", "inventory", "kill", "sign", "username").contains(type)) type = "unknown";
        return new Entry(query.raw(), type, timestamp, time, actor, "unclassified", "", sign,
                "", "", "", "", "unrecognized");
    }

    public static Location location(MessageData message) {
        var match = CoreProtectParser.COORDS.matcher(message.text());
        if (!match.find()) return Location.EMPTY;
        var hint = ACTION_HINT.matcher(message.text());
        return new Location(match.group(), match.group(4) == null ? "" : match.group(4),
                match.group(1), match.group(2), match.group(3), hint.find() ? hint.group(1).toLowerCase(Locale.ROOT) : "");
    }
}
