package com.ispf.driver.haystack;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal Project Haystack JSON v3 grid codec for {@code read} requests/responses (BL-61).
 */
final class HaystackJsonGrid {

    static final String JSON_MEDIA_TYPE = "application/vnd.haystack+json;version=3";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HaystackJsonGrid() {
    }

    static String buildReadRequest(List<String> refs) {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < refs.size(); i++) {
            if (i > 0) {
                rows.append(',');
            }
            rows.append("{\"id\":{\"_kind\":\"ref\",\"val\":\"")
                    .append(escapeJson(refs.get(i)))
                    .append("\"}}");
        }
        return """
                {"_kind":"grid","meta":{"ver":"3.0"},"cols":[{"name":"id"}],"rows":[%s]}
                """.formatted(rows);
    }

    static String buildAboutRequest() {
        return """
                {"_kind":"grid","meta":{"ver":"3.0"},"cols":[{"name":"serverName"}],"rows":[]}
                """;
    }

    static Map<String, HaystackPointValue> parseReadResponse(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> grid = OBJECT_MAPPER.readValue(body, new TypeReference<>() {
            });
            Object rowsRaw = grid.get("rows");
            if (!(rowsRaw instanceof List<?> rows)) {
                return Map.of();
            }
            Map<String, HaystackPointValue> values = new LinkedHashMap<>();
            for (Object rowRaw : rows) {
                if (!(rowRaw instanceof Map<?, ?> row)) {
                    continue;
                }
                String ref = extractRef(row.get("id"));
                if (ref.isBlank()) {
                    continue;
                }
                values.put(ref, new HaystackPointValue(
                        ref,
                        row.get("curVal"),
                        stringField(row.get("unit")),
                        stringField(row.get("dis"))
                ));
            }
            return Map.copyOf(values);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static String extractRef(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Object val = map.get("val");
            if (val != null) {
                return val.toString().trim();
            }
        }
        return raw == null ? "" : raw.toString().trim();
    }

    private static String stringField(Object raw) {
        return raw == null ? "" : raw.toString().trim();
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    record HaystackPointValue(String ref, Object curVal, String unit, String dis) {
    }
}
