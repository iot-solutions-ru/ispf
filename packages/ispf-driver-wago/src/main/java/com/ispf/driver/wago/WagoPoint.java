package com.ispf.driver.wago;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Point mapping for WAGO Modbus-TCP holding registers.
 * <p>
 * Accepted forms: {@code HR:100}, {@code HR:100:2}, {@code 100}, {@code MW100}, {@code MW:100},
 * {@code MW:100:2}. Lab mapping is 1:1 — the numeric suffix is the Modbus holding-register address.
 * {@code MW100} and {@code HR:100} both address register 100.
 */
public record WagoPoint(int address, int count) {

    private static final Pattern COMPACT_MW = Pattern.compile("^[Mm][Ww](\\d+)$");

    public static WagoPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("WAGO point mapping is blank");
        }
        String trimmed = raw.trim();
        if (trimmed.contains(":")) {
            String[] parts = trimmed.split(":");
            if (parts.length < 2 || parts.length > 3) {
                throw new IllegalArgumentException("WAGO point must be HR:address[:count] or MW:address[:count]");
            }
            String area = parts[0].trim().toUpperCase(Locale.ROOT);
            if (!"HR".equals(area) && !"HOLDING".equals(area) && !"MW".equals(area)) {
                throw new IllegalArgumentException("WAGO v0.1 supports HR/MW holding registers only, got: " + area);
            }
            int address = Integer.parseInt(parts[1].trim());
            int count = parts.length == 3 ? Integer.parseInt(parts[2].trim()) : 1;
            return new WagoPoint(address, requirePositive(count));
        }
        Matcher mwMatcher = COMPACT_MW.matcher(trimmed);
        if (mwMatcher.matches()) {
            return new WagoPoint(Integer.parseInt(mwMatcher.group(1)), 1);
        }
        return new WagoPoint(Integer.parseInt(trimmed), 1);
    }

    private static int requirePositive(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("WAGO point count must be >= 1");
        }
        return count;
    }
}
