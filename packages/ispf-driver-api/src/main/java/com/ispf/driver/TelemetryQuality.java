package com.ispf.driver;

import java.util.Locale;

/**
 * Normalized telemetry quality (BL-82, ADR-0025).
 */
public final class TelemetryQuality {

    public enum Level {
        GOOD,
        UNCERTAIN,
        BAD;

        public boolean isPlottable() {
            return this != BAD;
        }

        public String apiName() {
            return name();
        }
    }

    private TelemetryQuality() {
    }

    public static Level parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Level.GOOD;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "GOOD" -> Level.GOOD;
            case "UNCERTAIN", "UNCERT" -> Level.UNCERTAIN;
            case "BAD" -> Level.BAD;
            default -> Level.UNCERTAIN;
        };
    }

    public static boolean isPlottable(String raw) {
        return parse(raw).isPlottable();
    }

    public static String normalize(Level level) {
        return level != null ? level.apiName() : Level.GOOD.apiName();
    }
}
