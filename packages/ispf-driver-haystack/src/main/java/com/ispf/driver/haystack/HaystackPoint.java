package com.ispf.driver.haystack;

/**
 * Haystack ref from point mapping ({@code site.equip.temp} or {@code @site.equip.temp}).
 */
public record HaystackPoint(String ref) {

    public HaystackPoint {
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("Haystack ref is blank");
        }
    }

    public static HaystackPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Haystack point mapping is blank");
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("@")) {
            trimmed = trimmed.substring(1).trim();
        }
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Haystack ref is blank");
        }
        return new HaystackPoint(trimmed);
    }
}
