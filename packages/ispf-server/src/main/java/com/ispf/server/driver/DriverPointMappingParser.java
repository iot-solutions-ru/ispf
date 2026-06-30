package com.ispf.server.driver;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses {@code driverPointMappingsJson} supporting legacy {@code variable → pointId} strings
 * and extended objects with Haystack metadata (BL-59).
 */
public final class DriverPointMappingParser {

    private DriverPointMappingParser() {
    }

    public record Entry(String pointId, List<String> haystackTags, String unit, String dis) {

        public Entry {
            haystackTags = haystackTags != null ? List.copyOf(haystackTags) : List.of();
            unit = unit != null ? unit : "";
            dis = dis != null ? dis : "";
        }

        public static Entry ofPoint(String pointId) {
            return new Entry(pointId != null ? pointId : "", List.of(), "", "");
        }

        public boolean hasHaystackMetadata() {
            return !haystackTags.isEmpty() || !unit.isBlank() || !dis.isBlank();
        }
    }

    public static Map<String, Entry> parse(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {
            });
            Map<String, Entry> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> item : raw.entrySet()) {
                if (item.getKey() == null || item.getKey().isBlank()) {
                    continue;
                }
                result.put(item.getKey(), parseEntry(item.getValue()));
            }
            return Map.copyOf(result);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    public static Map<String, String> toPointIds(Map<String, Entry> entries) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> item : entries.entrySet()) {
            result.put(item.getKey(), item.getValue().pointId());
        }
        return Map.copyOf(result);
    }

    private static Entry parseEntry(Object value) {
        if (value instanceof String text) {
            return Entry.ofPoint(text);
        }
        if (value instanceof Map<?, ?> map) {
            String pointId = firstNonBlank(map, "point", "address", "pointId");
            Object tagsRaw = map.containsKey("haystackTags") ? map.get("haystackTags") : map.get("tags");
            String unit = stringField(map.get("unit"));
            String dis = stringField(map.get("dis"));
            return new Entry(pointId != null ? pointId : "", parseTagsList(tagsRaw), unit, dis);
        }
        return Entry.ofPoint(value != null ? value.toString() : "");
    }

    private static String firstNonBlank(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (!map.containsKey(key)) {
                continue;
            }
            String text = stringField(map.get(key));
            if (!text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private static String stringField(Object raw) {
        return raw == null ? "" : raw.toString().trim();
    }

    private static List<String> parseTagsList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<String> tags = new ArrayList<>();
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                String text = item.toString().trim();
                if (!text.isEmpty()) {
                    tags.add(text);
                }
            }
            return List.copyOf(tags);
        }
        if (raw instanceof String text && !text.isBlank()) {
            return List.of(text.trim());
        }
        return List.of();
    }
}
