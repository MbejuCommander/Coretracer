package dev.coretrace.core;

import java.util.ArrayList;
import java.util.List;

/** Finds a durable overlap without treating equal timestamps as unique event IDs. */
final class SmartOverlap {
    private SmartOverlap() {}
    record Event(String fingerprint, int start, int end) {}

    static List<Event> events(List<MessageData> messages, EventParser.Query query) {
        var result = new ArrayList<Event>();
        for (int i = 0; i < messages.size(); i++) {
            MessageData m = messages.get(i);
            if (!CoreProtectParser.entry(m)) continue;
            int end = i + 1;
            var parsed = EventParser.parse(m, query);
            String body = m.text().replaceFirst("^\\s*.*?\\s+[+−-]\\s+", "");
            StringBuilder key = new StringBuilder(parsed.timestamp()).append('\u0000').append(parsed.actor())
                    .append('\u0000').append(parsed.action()).append('\u0000').append(parsed.sign())
                    .append('\u0000').append(parsed.amount()).append('\u0000').append(parsed.object())
                    .append('\u0000').append(parsed.content()).append('\u0000').append(body)
                    .append('\u0000').append(m.struckThrough());
            if (end < messages.size() && CoreProtectParser.coordinates(messages.get(end))) {
                MessageData coordinate = messages.get(end++);
                // The caret's leading spaces change as CoreProtect's relative-time text grows.
                // Match the actual location and action hint, never its visual alignment.
                EventParser.Location location = EventParser.location(coordinate);
                key.append('\u0000').append(location.coordinates()).append('\u0000').append(location.hint());
            }
            result.add(new Event(key.toString(), i, end));
        }
        return result;
    }

    static int find(List<Event> haystack, List<Event> needle) {
        if (needle.size() < 3 || needle.stream().map(Event::fingerprint).distinct().count() < 3) return -1;
        outer: for (int i = 0; i <= haystack.size() - needle.size(); i++) {
            for (int j = 0; j < needle.size(); j++)
                if (!haystack.get(i + j).fingerprint().equals(needle.get(j).fingerprint())) continue outer;
            return i;
        }
        return -1;
    }

    static List<MessageData> edge(List<MessageData> messages, boolean first) {
        int count = 0;
        if (first) {
            int end = messages.size();
            for (int i = 0; i < messages.size(); i++)
                if (CoreProtectParser.entry(messages.get(i)) && ++count > 5) { end = i; break; }
            return List.copyOf(messages.subList(0, end));
        }
        int start = messages.size();
        for (int i = messages.size() - 1; i >= 0; i--)
            if (CoreProtectParser.entry(messages.get(i))) {
                if (++count > 5) break;
                start = i;
            }
        return List.copyOf(messages.subList(start, messages.size()));
    }

    static List<MessageData> dataOnly(List<MessageData> messages) {
        var result = new ArrayList<MessageData>();
        for (MessageData m : messages)
            if (CoreProtectParser.entry(m) || CoreProtectParser.coordinates(m)) result.add(m);
        return result;
    }
}
