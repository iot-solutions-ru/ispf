package com.ispf.driver.template;

/**
 * Parses driver point mapping strings. Template format: {@code channel:address}.
 */
public record TemplatePoint(String channel, String address) {

    public static TemplatePoint parse(String mapping) {
        if (mapping == null || mapping.isBlank()) {
            throw new IllegalArgumentException("point mapping required");
        }
        String[] parts = mapping.split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("expected channel:address, got: " + mapping);
        }
        return new TemplatePoint(parts[0].trim(), parts[1].trim());
    }
}
