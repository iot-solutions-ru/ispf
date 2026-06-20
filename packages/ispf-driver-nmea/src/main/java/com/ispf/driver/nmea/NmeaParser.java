package com.ispf.driver.nmea;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parses NMEA 0183 sentences into field maps.
 */
public final class NmeaParser {

    private NmeaParser() {
    }

    public static Map<String, String> parseSentenceFields(String sentence) {
        if (sentence == null || sentence.isBlank()) {
            return Map.of();
        }
        String trimmed = sentence.trim();
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
