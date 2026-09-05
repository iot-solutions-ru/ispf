package com.ispf.driver.yaskawamemobus;

import java.util.Locale;

/**
 * Point mapping for Memobus holding registers: {@code HR:100}, {@code 100}, or {@code HR:100:1}.
 */
public record YaskawaMemobusPoint(int address, int count) {

    public static YaskawaMemobusPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Yaskawa Memobus point mapping is blank");
        }
        String trimmed = raw.trim();
        if (trimmed.contains(":")) {
            String[] parts = trimmed.split(":");
            String area = parts[0].trim().toUpperCase(Locale.ROOT);
            if (!"HR".equals(area) && !"HOLDING".equals(area)) {
                throw new IllegalArgumentException("Yaskawa Memobus v0.1 supports HR holding registers only");
            }
            if (parts.length < 2 || parts.length > 3) {
                throw new IllegalArgumentException("Yaskawa Memobus point must be HR:address[:count]");
            }
            int address = Integer.parseInt(parts[1].trim());
            int count = parts.length == 3 ? Integer.parseInt(parts[2].trim()) : 1;
            return new YaskawaMemobusPoint(address, requirePositive(count));
        }
        return new YaskawaMemobusPoint(Integer.parseInt(trimmed), 1);
    }

    private static int requirePositive(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Memobus point count must be >= 1");
        }
        return count;
    }
}
