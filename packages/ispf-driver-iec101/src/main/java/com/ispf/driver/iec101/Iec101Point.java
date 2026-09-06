package com.ispf.driver.iec101;

import java.util.Locale;

/**
 * Point mapping for the IEC101-lab codec.
 * <p>
 * Accepted forms:
 * <ul>
 *   <li>{@code 1001} — IOA only (defaults to short-float {@code M_ME_NC_1})</li>
 *   <li>{@code M_ME_NC_1:1001} / {@code M_SP_NA_1:1001} — type then IOA</li>
 *   <li>{@code 1001:FLOAT} / {@code 1001:BOOL} — IOA then hint</li>
 * </ul>
 */
public record Iec101Point(int ioa, Kind kind) {

    public enum Kind {
        MEASURED_FLOAT,
        SINGLE_POINT
    }

    public static Iec101Point parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("IEC101 point mapping is blank");
        }
        String trimmed = raw.trim();
        String[] parts = trimmed.split(":");
        if (parts.length == 1) {
            return new Iec101Point(parseIoa(parts[0]), Kind.MEASURED_FLOAT);
        }
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "IEC101 point must be IOA, TYPE:IOA, or IOA:HINT, got: " + trimmed);
        }
        String left = parts[0].trim();
        String right = parts[1].trim();
        if (isTypeToken(left)) {
            return new Iec101Point(parseIoa(right), kindFromToken(left));
        }
        if (isTypeToken(right)) {
            return new Iec101Point(parseIoa(left), kindFromToken(right));
        }
        throw new IllegalArgumentException(
                "IEC101 point type must be M_ME_NC_1/FLOAT or M_SP_NA_1/BOOL, got: " + trimmed);
    }

    private static int parseIoa(String token) {
        try {
            int ioa = Integer.parseInt(token.trim());
            if (ioa < 0 || ioa > 0xFFFFFF) {
                throw new IllegalArgumentException("IOA out of range: " + ioa);
            }
            return ioa;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid IEC101 IOA: " + token, e);
        }
    }

    private static boolean isTypeToken(String token) {
        String upper = token.toUpperCase(Locale.ROOT);
        return "M_ME_NC_1".equals(upper)
                || "FLOAT".equals(upper)
                || "M_SP_NA_1".equals(upper)
                || "BOOL".equals(upper)
                || "BOOLEAN".equals(upper)
                || "SP".equals(upper);
    }

    private static Kind kindFromToken(String token) {
        String upper = token.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "M_ME_NC_1", "FLOAT" -> Kind.MEASURED_FLOAT;
            case "M_SP_NA_1", "BOOL", "BOOLEAN", "SP" -> Kind.SINGLE_POINT;
            default -> throw new IllegalArgumentException("Unknown IEC101 type token: " + token);
        };
    }
}
