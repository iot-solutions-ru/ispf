package com.ispf.driver.iec62056;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Point mapping for IEC 62056-21 Mode C readout: an OBIS code such as {@code 1.8.0},
 * {@code 1-0:1.8.0}, or {@code 1-0:1.8.0*255}.
 */
public record Iec62056Point(String obis) {

    private static final Pattern OBIS = Pattern.compile(
            "^\\d+(?:-\\d+)?(?::\\d+)?(?:\\.\\d+){1,4}(?:\\*\\d+)?$"
    );

    public static Iec62056Point parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("IEC 62056-21 point mapping is blank");
        }
        String trimmed = raw.trim();
        if (!OBIS.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "IEC 62056-21 point must be an OBIS code like 1.8.0 or 1-0:1.8.0, got: " + trimmed);
        }
        return new Iec62056Point(trimmed);
    }

    /**
     * Whether a meter data-line OBIS token matches this point (exact, or reduced A.B.C form).
     */
    public boolean matchesLineObis(String lineObis) {
        if (lineObis == null || lineObis.isBlank()) {
            return false;
        }
        String a = normalize(obis);
        String b = normalize(lineObis);
        return a.equals(b) || a.endsWith(":" + b) || b.endsWith(":" + a)
                || core(a).equals(core(b));
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /** Strip channel / group prefix and attribute suffix: {@code 1-0:1.8.0*255} → {@code 1.8.0}. */
    static String core(String obis) {
        String value = normalize(obis);
        int colon = value.lastIndexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }
        int star = value.indexOf('*');
        if (star >= 0) {
            value = value.substring(0, star);
        }
        return value;
    }
}
