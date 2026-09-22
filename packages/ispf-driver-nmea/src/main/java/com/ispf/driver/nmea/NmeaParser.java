package com.ispf.driver.nmea;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parses NMEA 0183 sentences into field maps.
 * <p>
 * When a {@code *} checksum is present, validates it as the XOR of the bytes
 * between {@code $} and {@code *}, encoded as two hex digits.
 */
public final class NmeaParser {

    private NmeaParser() {
    }

    public static Map<String, String> parseSentenceFields(String sentence) {
        if (sentence == null || sentence.isBlank()) {
            return Map.of();
        }
        String trimmed = sentence.trim();
        if (!checksumOk(trimmed)) {
            return Map.of();
        }
        if (trimmed.startsWith("$")) {
            trimmed = trimmed.substring(1);
        }
        int star = trimmed.indexOf('*');
        if (star > 0) {
            trimmed = trimmed.substring(0, star);
        }
        String[] parts = trimmed.split(",", -1);
        if (parts.length == 0) {
            return Map.of();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("type", parts[0].toUpperCase(Locale.ROOT));
        for (int i = 1; i < parts.length; i++) {
            fields.put("f" + i, parts[i]);
        }
        return fields;
    }

    /**
     * NMEA 0183 checksum: XOR of US-ASCII bytes between {@code $} and {@code *},
     * compared to the two hex digits that follow {@code *}. Sentences without
     * {@code *} are accepted as-is.
     */
    static boolean checksumOk(String sentence) {
        int star = sentence.indexOf('*');
        if (star < 0) {
            return true;
        }
        int start = sentence.startsWith("$") ? 1 : 0;
        if (star <= start) {
            return false;
        }
        int xor = 0;
        byte[] body = sentence.substring(start, star).getBytes(StandardCharsets.US_ASCII);
        for (byte b : body) {
            xor ^= b & 0xFF;
        }
        String given = sentence.substring(star + 1).trim();
        if (given.length() < 2) {
            return false;
        }
        String expected = String.format(Locale.ROOT, "%02X", xor);
        return expected.equalsIgnoreCase(given.substring(0, 2));
    }

    public static String toJson(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escapeJson(entry.getKey())).append("\":");
            sb.append('"').append(escapeJson(entry.getValue())).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
