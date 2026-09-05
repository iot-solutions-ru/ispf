package com.ispf.driver.ansic12;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Point mapping for the ANSI C12 lab codec.
 * <p>
 * Accepted forms (all resolve to a standard table id):
 * <ul>
 *   <li>{@code table:1} / {@code table:01}</li>
 *   <li>{@code ST1} / {@code ST-1} / {@code st01}</li>
 *   <li>{@code 1} — bare table number</li>
 * </ul>
 */
public record AnsiC12Point(int tableId) {

    private static final Pattern TABLE_PREFIX = Pattern.compile(
            "^(?:table:|st-?|std?)(\\d+)$",
            Pattern.CASE_INSENSITIVE
    );

    public static AnsiC12Point parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("ANSI C12 point mapping is blank");
        }
        String trimmed = raw.trim();
        Matcher matcher = TABLE_PREFIX.matcher(trimmed);
        if (matcher.matches()) {
            return new AnsiC12Point(parseTable(matcher.group(1)));
        }
        if (trimmed.chars().allMatch(Character::isDigit)) {
            return new AnsiC12Point(parseTable(trimmed));
        }
        throw new IllegalArgumentException(
                "ANSI C12 point must be table:N, STN, or N, got: " + trimmed);
    }

    private static int parseTable(String token) {
        int tableId = Integer.parseInt(token);
        if (tableId < 0 || tableId > 0xFFFF) {
            throw new IllegalArgumentException("ANSI C12 table id out of range: " + tableId);
        }
        return tableId;
    }

    public String label() {
        return "ST" + tableId;
    }

    @Override
    public String toString() {
        return "table:" + tableId + " (" + label().toUpperCase(Locale.ROOT) + ")";
    }
}
