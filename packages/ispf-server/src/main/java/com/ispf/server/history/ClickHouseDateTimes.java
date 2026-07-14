package com.ispf.server.history;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

/**
 * ClickHouse DateTime / DateTime64 string formats used over the HTTP interface.
 * Aggregate {@code toStartOfInterval} often returns whole seconds without a fraction.
 */
final class ClickHouseDateTimes {

    /** Wire format for parameters and INSERT JSON (always with millis). */
    static final DateTimeFormatter WRITE = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter READ = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.MILLI_OF_SECOND, 1, 3, true)
            .optionalEnd()
            .toFormatter()
            .withZone(ZoneOffset.UTC);

    private ClickHouseDateTimes() {
    }

    static Instant parse(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        String normalized = value.trim();
        if (normalized.contains("T")) {
            return Instant.parse(normalized.endsWith("Z") || normalized.contains("+")
                    ? normalized
                    : normalized + "Z");
        }
        return READ.parse(normalized, Instant::from);
    }

    static Instant parseOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parse(value);
    }
}
