package dev.coretrace.core;

import java.util.List;

/** A copy of a system-chat component. Click actions are metadata, never executed. */
public record MessageData(String text, List<String> hovers, List<String> lookupLinks, long receivedAt, boolean struckThrough) {
    public MessageData(String text, List<String> hovers, List<String> lookupLinks, long receivedAt) {
        this(text, hovers, lookupLinks, receivedAt, false);
    }
    public MessageData {
        text = clean(text);
        hovers = hovers.stream().map(MessageData::clean).distinct().limit(16).toList();
        lookupLinks = List.copyOf(lookupLinks);
    }

    public static MessageData plain(String s, long time) {
        return new MessageData(s, List.of(), List.of(), time);
    }

    public static String clean(String value) {
        if (value == null) return "";
        String s = value.replaceAll("§[0-9a-fk-orxA-FK-ORX]", "")
                .replaceAll("[\\p{Cc}&&[^\\n\\t]]", "");
        return s.length() <= 32768 ? s : s.substring(0, 32768);
    }
}
