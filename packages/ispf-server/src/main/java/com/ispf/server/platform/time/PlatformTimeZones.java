package com.ispf.server.platform.time;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;

/**
 * IANA timezone validation and normalization (ADR-0020).
 */
public final class PlatformTimeZones {

    public static final String DEFAULT = "UTC";

    private PlatformTimeZones() {
    }

    public static String normalizeOrDefault(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        return normalize(raw.trim());
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("timeZone is required");
        }
        String candidate = raw.trim();
        if (DEFAULT.equalsIgnoreCase(candidate)) {
            return DEFAULT;
        }
        try {
            return ZoneId.of(candidate).getId();
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Invalid IANA timeZone: " + candidate, ex);
        }
    }

    public static boolean isValid(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        try {
            ZoneId.of(raw.trim());
            return true;
        } catch (DateTimeException ex) {
            return false;
        }
    }

    public static String displayLabel(String zoneId) {
        String normalized = normalizeOrDefault(zoneId);
        if (DEFAULT.equals(normalized)) {
            return "UTC";
        }
        return normalized.replace('_', ' ');
    }
}
