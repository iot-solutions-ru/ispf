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

    /** Same characters as {@link #WRITE}, without the formatter allocation. */
    static void appendWrite(StringBuilder body, Instant instant) {
        long epochMilli = instant.toEpochMilli();
        long epochSecond = Math.floorDiv(epochMilli, 1000);
        int milli = (int) Math.floorMod(epochMilli, 1000);
        long days = Math.floorDiv(epochSecond, 86_400);
        int secondOfDay = (int) Math.floorMod(epochSecond, 86_400);
        long z = days + 719_468;
        long era = (z >= 0 ? z : z - 146_096) / 146_097;
        long doe = z - era * 146_097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long year = yoe + era * 400;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        long day = doy - (153 * mp + 2) / 5 + 1;
        long month = mp + (mp < 10 ? 3 : -9);
        if (month <= 2) {
            year++;
        }
        if (year < 0 || year > 9999) {
            body.append(WRITE.format(instant));
            return;
        }
        appendDigits(body, (int) year, 4);
        body.append('-');
        appendDigits(body, (int) month, 2);
        body.append('-');
        appendDigits(body, (int) day, 2);
        body.append(' ');
        appendDigits(body, secondOfDay / 3600, 2);
        body.append(':');
        appendDigits(body, (secondOfDay % 3600) / 60, 2);
        body.append(':');
        appendDigits(body, secondOfDay % 60, 2);
        body.append('.');
        appendDigits(body, milli, 3);
    }

    private static void appendDigits(StringBuilder body, int value, int width) {
        int start = body.length();
        for (int i = 0; i < width; i++) {
            body.append((char) ('0' + (value % 10)));
            value /= 10;
        }
        int end = body.length();
        for (int i = 0; i < width / 2; i++) {
            char left = body.charAt(start + i);
            body.setCharAt(start + i, body.charAt(end - 1 - i));
            body.setCharAt(end - 1 - i, left);
        }
    }
}
