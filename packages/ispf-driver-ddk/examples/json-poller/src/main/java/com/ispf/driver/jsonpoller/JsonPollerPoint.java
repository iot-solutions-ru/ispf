package com.ispf.driver.jsonpoller;

/**
 * Parses point mapping {@code jsonPath:$.field}.
 */
public record JsonPollerPoint(String jsonPath) {

    public static JsonPollerPoint parse(String mapping) {
        if (mapping == null || mapping.isBlank()) {
            throw new IllegalArgumentException("point mapping required");
        }
        String[] parts = mapping.split(":", 2);
        if (parts.length != 2 || !"jsonPath".equals(parts[0].trim()) || parts[1].isBlank()) {
            throw new IllegalArgumentException("expected jsonPath:$.field, got: " + mapping);
        }
        return new JsonPollerPoint(parts[1].trim());
    }
}
