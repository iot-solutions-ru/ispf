package com.ispf.driver.ieee20305;

/**
 * Point mapping for IEEE 2030.5 (SEP2) HTTP resources.
 * <p>
 * Forms:
 * <ul>
 *   <li>{@code /edev} — GET EndDeviceList (default field {@code sFDI})</li>
 *   <li>{@code /edev:sFDI} — resource path with named XML element</li>
 *   <li>{@code /upt/1/mr/1/r:value} — MeterReading / Reading path + field</li>
 * </ul>
 */
public record Ieee20305Point(String path, String field) {

    public static Ieee20305Point parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("IEEE 2030.5 point mapping is blank");
        }
        String trimmed = raw.trim();
        if (!trimmed.startsWith("/")) {
            throw new IllegalArgumentException(
                    "IEEE 2030.5 point must be an absolute SEP2 path like /edev or /upt/1/mr/1/r:value");
        }

        int sep = trimmed.lastIndexOf(':');
        if (sep > 0) {
            String maybePath = trimmed.substring(0, sep);
            String maybeField = trimmed.substring(sep + 1).trim();
            if (maybePath.startsWith("/") && !maybeField.isEmpty() && !maybeField.contains("/")) {
                return new Ieee20305Point(maybePath, maybeField);
            }
        }
        return new Ieee20305Point(trimmed, defaultField(trimmed));
    }

    private static String defaultField(String path) {
        String lower = path.toLowerCase();
        if (lower.contains("edev")) {
            return "sFDI";
        }
        if (lower.contains("/mr") || lower.endsWith("/r") || lower.contains("reading")) {
            return "value";
        }
        return "href";
    }
}
