package dev.coretrace.core;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/** Validates server order without collapsing legitimate events that share a timestamp. */
public final class SmartCsvOrder {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);
    private LocalDateTime previous;
    private String zone;
    public String accept(String timestamp) {
        if (timestamp == null || timestamp.length() < 19) return "smart.missing_time";
        try {
            LocalDateTime next = LocalDateTime.parse(timestamp.substring(0, 19), FORMAT);
            String nextZone = timestamp.substring(19).strip();
            if (previous != null && (!nextZone.equals(zone) || next.isAfter(previous))) return "smart.changed_order";
            previous = next; zone = nextZone;
            return null;
        } catch (java.time.DateTimeException e) { return "smart.missing_time"; }
    }
}
